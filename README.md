# SafeHome: IoT Usability vs. Security Research Platform

> **Research Topic:** Evaluating the Usability vs. Security Trade-Off in Consumer IoT  
> **Course / Module:** ITMDA (Information Technology / Multimedia / Data Analytics) — Group 14  
> **Tech Stack:** Android (Kotlin, Jetpack Compose), Python (Flask, SQLite), Excel Reporting (Pandas, OpenPyXL)

---

## Overview

Consumer smart home (IoT) devices often prioritize out-of-the-box convenience over security. Most devices ship with factory defaults that maximize convenience (simple PINs, unencrypted communication, and open ports) at the cost of high security risks. Hardening these devices often introduces user friction.

**SafeHome** is a research platform designed to measure how everyday users navigate these trade-offs. The mobile app presents a live security auditor that recalculates a **Security Score** and **Convenience Index** in real time as participants choose how to configure their gateway across three core areas:
1. **Access & Identity:** Device authentication credentials and action authorization thresholds.
2. **Network & Connectivity:** Transport protocol encryption, Wi-Fi VLAN isolation, and remote firewall exposure.
3. **System & Privacy:** Firmware patch policies and cloud telemetry sharing.

Once commissioning is complete, the app transitions into an operational **Smart Home Hub** where participants can interact with live temperature readings, lights, and climate controls.

---

## System Architecture

* **Android Mobile App (`android_app/`):** Built natively with Kotlin and Jetpack Compose. Communicates via REST API to receive score calculations, display live gauges, and submit final gateway configurations.
* **Flask REST API (`app.py`, `models.py`):** Handles participant authentication, computes security vs. usability metrics, logs interaction telemetry, and stores data in SQLite (`database.db`).
* **Reporting Pipeline (`export_results.py`):** Automatically compiles study data into a formatted multi-tab Excel spreadsheet (`study_results.xlsx`).

---

## Local Setup & Testing

### 1. Start the Python Backend
1. Open a terminal in the project root directory.
2. Activate the virtual environment:
   ```powershell
   .\.venv\Scripts\Activate.ps1
   ```
3. Install required packages (if not already installed):
   ```bash
   pip install -r requirements.txt
   ```
4. Run the API server:
   ```bash
   python app.py
   ```
   The backend will start locally on `http://127.0.0.1:5000`.

---

### 2. Run the Android App in Android Studio
1. Launch **Android Studio**.
2. Select **File > Open...** and choose the `android_app` folder.
3. Allow Gradle to finish syncing project dependencies.
4. Run the app on an emulator or physical Android phone:
   * **Android Emulator:** Uses `http://10.0.2.2:5000/` by default to connect to your PC's Flask server.
   * **Physical Phone:** Connect your phone and PC to the same Wi-Fi network. On the app's login screen, tap the **gear icon** in the top right and enter your PC's local IP address (e.g. `http://192.168.1.150:5000/`).

---

### 3. How to Download the Research Results (Admin Web API)

The study data is not accessible to participants within the mobile app. Administrators and researchers can download the study files directly via browser links:

* **Download Excel Report (`.xlsx`):**
  ```text
  http://localhost:5000/api/export/excel
  ```
* **Download Raw Database (`database.db`):**
  ```text
  http://localhost:5000/api/export/database
  ```
  *(Or `http://localhost:5000/api/export/db`)*
* **Via terminal:** Run the export script to generate `results/study_results.xlsx`:
  ```bash
  python export_results.py
  ```

---

## Privacy Note
To protect participant privacy:
* No names or plaintext emails are stored in the database.
* User accounts are keyed using a salted SHA-256 hash.
* All exported research records reference pseudonymized IDs (e.g. `P-001`, `P-002`).
