"""
Automated Research Reporting Pipeline.
Extracts participant data, commissioning sessions, and interaction telemetry
from SQLite and formats them into a publication-ready Excel spreadsheet.
"""

import os
import sys
import json
import sqlite3
import argparse
from datetime import datetime, timezone
import pandas as pd
import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.path.join(BASE_DIR, "database.db")
RESULTS_DIR = os.path.join(BASE_DIR, "results")
DEFAULT_EXCEL_OUTPUT = os.path.join(RESULTS_DIR, "study_results.xlsx")


def get_local_tz_info():
    """Detects local machine timezone and formats UTC offset for reporting."""
    now_local = datetime.now().astimezone()
    tz_name = now_local.tzname() or "Local Time"
    utc_offset = now_local.strftime("%z")
    offset_formatted = f"UTC{utc_offset[:3]}:{utc_offset[3:]}" if utc_offset else "UTC+02:00"
    return now_local.tzinfo, tz_name, offset_formatted


def format_duration(seconds):
    """Converts raw seconds into a formatted MM:SS string."""
    if pd.isna(seconds) or seconds is None:
        return "N/A"
    try:
        total_secs = int(round(float(seconds)))
        mins = total_secs // 60
        secs = total_secs % 60
        return f"{mins:02d}:{secs:02d}"
    except (ValueError, TypeError):
        return "N/A"


def parse_and_convert_dates(series, local_tz):
    """Converts UTC timestamps into both localized string and standardized UTC string."""
    dt_utc = pd.to_datetime(series, utc=True, errors="coerce")
    dt_local = dt_utc.dt.tz_convert(local_tz)
    local_str = dt_local.dt.strftime("%Y-%m-%d %H:%M:%S").fillna("N/A")
    utc_str = dt_utc.dt.strftime("%Y-%m-%d %H:%M:%S").fillna("N/A")
    return local_str, utc_str


def build_kpi_summary(sessions_df, users_df):
    """
    Computes aggregate metrics across the participant cohort:
    - Total participants, completion rate
    - Average security score vs average convenience index
    - Grade distributions (A, B, C, D)
    - Mean task duration, toggle adjustments, and warning reversal nudges
    """
    metrics = []
    total_u = len(users_df) if not users_df.empty else 0
    comp = sessions_df[sessions_df["status"] == "commissioned"] if not sessions_df.empty and "status" in sessions_df.columns else pd.DataFrame()
    metrics.append(("Total Registered Participants", total_u))
    metrics.append(("Commissioned Devices (Completed)", len(comp)))
    comp_rate = (len(comp) / total_u * 100) if total_u else 0.0
    metrics.append(("Completion Rate (%)", f"{comp_rate:.1f}%"))

    avg_sec = comp["final_security_score"].mean() if not comp.empty and "final_security_score" in comp.columns else 0.0
    avg_use = comp["final_usability_score"].mean() if not comp.empty and "final_usability_score" in comp.columns else 0.0
    metrics.append(("Mean Security Health Score (0-100)", f"{avg_sec:.1f}%"))
    metrics.append(("Mean Usability / Convenience Index (0-100)", f"{avg_use:.1f}%"))

    # Security grade breakdown (safe against empty dataframe or missing column)
    grade_a = len(comp[comp["security_grade"] == "A"]) if not comp.empty and "security_grade" in comp.columns else 0
    grade_b = len(comp[comp["security_grade"] == "B"]) if not comp.empty and "security_grade" in comp.columns else 0
    grade_c = len(comp[comp["security_grade"] == "C"]) if not comp.empty and "security_grade" in comp.columns else 0
    grade_d = len(comp[comp["security_grade"] == "D"]) if not comp.empty and "security_grade" in comp.columns else 0
    comp_len = len(comp)
    metrics.append(("Grade A (Hardened Zero-Trust)", f"{grade_a} ({grade_a/comp_len*100:.1f}%)" if comp_len else "0"))
    metrics.append(("Grade B (Balanced Consumer)", f"{grade_b} ({grade_b/comp_len*100:.1f}%)" if comp_len else "0"))
    metrics.append(("Grade C/D (High Vulnerability / Low Security)", f"{grade_c+grade_d} ({(grade_c+grade_d)/comp_len*100:.1f}%)" if comp_len else "0"))

    # Task duration and interaction metrics
    avg_dur = comp["total_time_seconds"].mean() if not comp.empty and "total_time_seconds" in comp.columns else 0.0
    avg_toggles = comp["toggle_count"].mean() if not comp.empty and "toggle_count" in comp.columns else 0.0
    avg_reversals = comp["warning_reversals"].mean() if not comp.empty and "warning_reversals" in comp.columns else 0.0

    metrics.append(("Average Configuration Time", format_duration(avg_dur)))
    metrics.append(("Average Settings Adjusted (Toggles Flipped)", f"{avg_toggles:.1f}"))
    metrics.append(("Average Warning Reversals (Nudge Rate)", f"{avg_reversals:.2f}"))

    return pd.DataFrame(metrics, columns=["Usability vs Security Metric", "Value"])


def load_database_data(db_path, local_tz, offset_label):
    """Connects to SQLite, reads data tables into DataFrames, and converts timestamps."""
    if not os.path.exists(db_path):
        return pd.DataFrame(), pd.DataFrame(), pd.DataFrame(), build_kpi_summary(pd.DataFrame(), pd.DataFrame())

    conn = sqlite3.connect(db_path)

    users_df = pd.read_sql_query(
        "SELECT id AS user_id, email_hash AS participant_hash, created_at FROM users ORDER BY id ASC",
        conn
    )
    sessions_df = pd.read_sql_query(
        "SELECT * FROM commission_sessions ORDER BY id ASC",
        conn
    )
    logs_df = pd.read_sql_query(
        "SELECT * FROM interaction_logs ORDER BY id ASC",
        conn
    )
    conn.close()

    # Normalize historical statuses and filter out legacy abandonment logs
    if not sessions_df.empty and "status" in sessions_df.columns:
        sessions_df["status"] = sessions_df["status"].replace({"abandoned": "in_progress"})

    if not logs_df.empty and "event_type" in logs_df.columns:
        logs_df = logs_df[logs_df["event_type"] != "session_abandoned"]

    kpi_df = build_kpi_summary(sessions_df, users_df)

    if not users_df.empty:
        u_loc, u_utc = parse_and_convert_dates(users_df["created_at"], local_tz)
        users_df["registered_local"] = u_loc
        users_df["registered_utc"] = u_utc

    if not sessions_df.empty:
        s_start_loc, s_start_utc = parse_and_convert_dates(sessions_df["started_at"], local_tz)
        s_end_loc, s_end_utc = parse_and_convert_dates(sessions_df["completed_at"], local_tz)
        sessions_df["started_local"] = s_start_loc
        sessions_df["completed_local"] = s_end_loc
        sessions_df["duration_formatted"] = sessions_df["total_time_seconds"].apply(format_duration)

    if not logs_df.empty:
        l_loc, l_utc = parse_and_convert_dates(logs_df["timestamp"], local_tz)
        logs_df["timestamp_local"] = l_loc
        logs_df["timestamp_utc"] = l_utc

    return users_df, sessions_df, logs_df, kpi_df


def generate_excel_workbook(db_path=DB_PATH):
    """Builds and returns the styled openpyxl Workbook from database data."""
    local_tz, tz_name, offset_label = get_local_tz_info()
    users_df, sessions_df, logs_df, kpi_df = load_database_data(db_path, local_tz, offset_label)

    wb = openpyxl.Workbook()

    # Styling constants
    NAVY_DARK = "1E293B"
    ROYAL_BLUE = "2563EB"
    WHITE = "FFFFFF"
    BORDER_COLOR = "CBD5E1"

    thin_border = Border(
        left=Side(style="thin", color=BORDER_COLOR),
        right=Side(style="thin", color=BORDER_COLOR),
        top=Side(style="thin", color=BORDER_COLOR),
        bottom=Side(style="thin", color=BORDER_COLOR)
    )

    # -----------------------------------------------------------------------
    # 1. Executive Summary Sheet
    # -----------------------------------------------------------------------
    ws_exec = wb.active
    ws_exec.title = "Executive_Summary"
    ws_exec.views.sheetView[0].showGridLines = True

    # Title banner
    ws_exec.merge_cells("B2:D2")
    ws_exec["B2"] = "IoT Usability vs. Security Trade-Off Research Analytics"
    ws_exec["B2"].font = Font(name="Segoe UI", size=14, bold=True, color=WHITE)
    ws_exec["B2"].fill = PatternFill(start_color=NAVY_DARK, end_color=NAVY_DARK, fill_type="solid")
    ws_exec["B2"].alignment = Alignment(horizontal="center", vertical="center")
    ws_exec.row_dimensions[2].height = 36

    ws_exec.merge_cells("B3:D3")
    ws_exec["B3"] = "Commissioning Auditor Dataset | SafeHome Framework"
    ws_exec["B3"].font = Font(name="Segoe UI", size=10, bold=True, color=WHITE)
    ws_exec["B3"].fill = PatternFill(start_color=ROYAL_BLUE, end_color=ROYAL_BLUE, fill_type="solid")
    ws_exec["B3"].alignment = Alignment(horizontal="center", vertical="center")
    ws_exec.row_dimensions[3].height = 22

    # KPI table header
    ws_exec["B5"] = "Usability vs Security Metric"
    ws_exec["B5"].font = Font(name="Segoe UI", size=10, bold=True, color=WHITE)
    ws_exec["B5"].fill = PatternFill(start_color=NAVY_DARK, end_color=NAVY_DARK, fill_type="solid")
    ws_exec["B5"].border = thin_border

    ws_exec.merge_cells("C5:D5")
    ws_exec["C5"] = "Study Benchmark Value"
    ws_exec["C5"].font = Font(name="Segoe UI", size=10, bold=True, color=WHITE)
    ws_exec["C5"].fill = PatternFill(start_color=NAVY_DARK, end_color=NAVY_DARK, fill_type="solid")
    ws_exec["C5"].alignment = Alignment(horizontal="center", vertical="center")
    ws_exec["C5"].border = thin_border
    ws_exec["D5"].border = thin_border

    # KPI data rows
    row_idx = 6
    for _, row in kpi_df.iterrows():
        ws_exec.row_dimensions[row_idx].height = 22
        cell_m = ws_exec.cell(row=row_idx, column=2, value=row["Usability vs Security Metric"])
        cell_m.font = Font(name="Segoe UI", size=10, bold=("Mean" in str(row["Usability vs Security Metric"])))
        cell_m.border = thin_border

        ws_exec.merge_cells(start_row=row_idx, start_column=3, end_row=row_idx, end_column=4)
        cell_v = ws_exec.cell(row=row_idx, column=3, value=row["Value"])
        cell_v.font = Font(name="Segoe UI", size=10)
        cell_v.alignment = Alignment(horizontal="center", vertical="center")
        cell_v.border = thin_border
        ws_exec.cell(row=row_idx, column=4).border = thin_border
        row_idx += 1

    ws_exec.column_dimensions["B"].width = 45
    ws_exec.column_dimensions["C"].width = 25
    ws_exec.column_dimensions["D"].width = 25

    # -----------------------------------------------------------------------
    # 2. Participant Audits Sheet
    # -----------------------------------------------------------------------
    ws_audits = wb.create_sheet(title="Participant_Audits")
    ws_audits.views.sheetView[0].showGridLines = True
    audit_cols = [
        "Session ID", "User ID", "Status", "Security Score (%)", "Usability Index (%)",
        "Grade", "Risk Tier", "Duration (MM:SS)", "Toggles Adjusted", "Warning Reversals"
    ]
    ws_audits.append(audit_cols)
    ws_audits.row_dimensions[1].height = 26
    for cell in ws_audits[1]:
        cell.font = Font(name="Segoe UI", size=10, bold=True, color=WHITE)
        cell.fill = PatternFill(start_color=NAVY_DARK, end_color=NAVY_DARK, fill_type="solid")
        cell.border = thin_border
        cell.alignment = Alignment(horizontal="center", vertical="center")

    if not sessions_df.empty:
        for _, s in sessions_df.iterrows():
            status_val = "Commissioned" if str(s.get("status", "")).lower() == "commissioned" else "In Progress"
            ws_audits.append([
                s.get("id"),
                f"P-{s.get('user_id', 1):03d}",
                status_val,
                s.get("final_security_score"),
                s.get("final_usability_score"),
                s.get("security_grade"),
                s.get("risk_tier"),
                s.get("duration_formatted", "N/A"),
                s.get("toggle_count", 0),
                s.get("warning_reversals", 0),
            ])

    # -----------------------------------------------------------------------
    # 3. Setting Choices Breakdown Sheet
    # -----------------------------------------------------------------------
    ws_choices = wb.create_sheet(title="Setting_Choices")
    ws_choices.views.sheetView[0].showGridLines = True
    setting_cols = [
        "User ID", "Authentication", "Transport Crypto", "Network Topology",
        "Remote Exposure", "Firmware Patching", "Cloud Privacy", "Action Authorization"
    ]
    ws_choices.append(setting_cols)
    ws_choices.row_dimensions[1].height = 26
    for cell in ws_choices[1]:
        cell.font = Font(name="Segoe UI", size=10, bold=True, color=WHITE)
        cell.fill = PatternFill(start_color=NAVY_DARK, end_color=NAVY_DARK, fill_type="solid")
        cell.border = thin_border
        cell.alignment = Alignment(horizontal="center", vertical="center")

    if not sessions_df.empty:
        for _, s in sessions_df.iterrows():
            ws_choices.append([
                f"P-{s.get('user_id', 1):03d}",
                s.get("auth_credential"),
                s.get("transport_crypto"),
                s.get("network_topology"),
                s.get("remote_exposure"),
                s.get("firmware_policy"),
                s.get("data_privacy"),
                s.get("action_auth"),
            ])

    # -----------------------------------------------------------------------
    # 4. Granular Interaction Telemetry Logs Sheet
    # -----------------------------------------------------------------------
    ws_logs = wb.create_sheet(title="Interaction_Logs")
    ws_logs.views.sheetView[0].showGridLines = True
    log_cols = ["Log ID", "Participant ID", "Event Type", f"Timestamp ({tz_name})", "Event Details"]
    ws_logs.append(log_cols)
    ws_logs.row_dimensions[1].height = 26
    for cell in ws_logs[1]:
        cell.font = Font(name="Segoe UI", size=10, bold=True, color=WHITE)
        cell.fill = PatternFill(start_color=NAVY_DARK, end_color=NAVY_DARK, fill_type="solid")
        cell.border = thin_border
        cell.alignment = Alignment(horizontal="center", vertical="center")

    if not logs_df.empty:
        for _, log in logs_df.iterrows():
            details_raw = log.get("event_details")
            details_str = json.dumps(details_raw) if isinstance(details_raw, (dict, list)) else str(details_raw or "")
            ws_logs.append([
                log.get("id"),
                f"P-{log.get('user_id', 1):03d}",
                log.get("event_type"),
                log.get("timestamp_local", "N/A"),
                details_str,
            ])

    # Auto-fit column widths across all data sheets
    for ws in [ws_audits, ws_choices, ws_logs]:
        for col in ws.columns:
            max_len = max(len(str(cell.value or "")) for cell in col)
            col_letter = get_column_letter(col[0].column)
            ws.column_dimensions[col_letter].width = min(max(max_len + 3, 14), 60)

    return wb


def generate_excel_report_bytes(db_path=DB_PATH):
    """Builds the Excel report directly into an in-memory BytesIO buffer."""
    import io
    wb = generate_excel_workbook(db_path)
    buffer = io.BytesIO()
    wb.save(buffer)
    buffer.seek(0)
    return buffer


def export_database_to_excel(output_path=None, db_path=DB_PATH):
    """Generates the multi-tab formatted Excel report and saves it to disk."""
    os.makedirs(RESULTS_DIR, exist_ok=True)
    target_file = output_path or DEFAULT_EXCEL_OUTPUT
    wb = generate_excel_workbook(db_path)
    try:
        wb.save(target_file)
        print(f"Report successfully saved to: {target_file}")
        return target_file
    except PermissionError:
        # If open in Microsoft Excel, save to a fallback file so export/download succeeds
        fallback_file = os.path.join(RESULTS_DIR, "study_results_latest.xlsx")
        wb.save(fallback_file)
        print(f"Target file locked; report saved to fallback: {fallback_file}")
        return fallback_file


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export IoT Usability vs Security Research to Excel.")
    parser.add_argument("--output", type=str, default=None, help="Custom output path")
    args = parser.parse_args()
    export_database_to_excel(output_path=args.output)
