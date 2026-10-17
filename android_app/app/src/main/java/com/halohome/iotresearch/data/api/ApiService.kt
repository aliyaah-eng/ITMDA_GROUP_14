package com.halohome.iotresearch.data.api

import com.halohome.iotresearch.data.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit REST API interface for the SafeHome backend.
 * Defines network endpoints for authentication, real-time auditing,
 * commissioning deployment, and interaction telemetry.
 */
interface ApiService {

    // --- Authentication & Profile Endpoints ---

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("api/user/profile")
    suspend fun getProfile(): Response<AuthResponse>

    // --- Commissioning & Security Auditor Endpoints ---

    /** Computes live security and usability scores without saving (powers the live gauges). */
    @POST("api/commission/calculate")
    suspend fun calculateScores(@Body request: CalculateRequest): Response<CalculateResponse>

    /** Seals the gateway configuration, records completion metrics, and generates the audit certificate. */
    @POST("api/commission/deploy")
    suspend fun deployGateway(@Body request: DeployRequest): Response<DeployResponse>

    // --- Telemetry & Interaction Tracking ---

    /** Logs participant UI actions, toggle adjustments, and dialog interactions. */
    @POST("api/telemetry/event")
    suspend fun logEvent(@Body request: TelemetryEventRequest): Response<Map<String, Any>>

    /** Periodic 10-second heartbeat signal to keep the active session alive. */
    @POST("api/telemetry/heartbeat")
    suspend fun sendHeartbeat(): Response<Map<String, Any>>

    /** Logs out the participant from the research session. */
    @POST("api/auth/logout")
    suspend fun logout(): Response<Map<String, Any>>

    /** Legacy alias for session logout. */
    @POST("api/telemetry/abandon")
    suspend fun abandonSession(@Body request: Map<String, String> = emptyMap()): Response<Map<String, Any>>
}
