package com.lumisound.android.bridge.model

import com.google.gson.annotations.SerializedName

/**
 * The bridge's `_user_dict` shape, returned by `/auth/register`, `/auth/login`
 * and `/auth/me`. Field names are the server's snake_case exactly -- this is the
 * same account row Lumisound on iOS reads, so nothing here is renamed for
 * Android's convenience.
 */
data class BridgeUser(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    @SerializedName("display_name") val displayName: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_login") val lastLogin: String? = null,
    @SerializedName("date_of_birth") val dateOfBirth: String? = null,
    @SerializedName("share_listening_activity") val shareListeningActivity: Boolean? = null,
    @SerializedName("ai_assisted_suggestions") val aiAssistedSuggestions: Boolean? = null,
)

data class LoginRequest(
    val username: String,
    val password: String,
    @SerializedName("device_name") val deviceName: String? = null,
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    @SerializedName("display_name") val displayName: String? = null,
)

/**
 * `/auth/login`'s response is one of two shapes: a real session (`user` +
 * `token`), or a 2FA challenge (`requires_2fa` + `pending_token`) with no
 * session created yet. Modeled as one nullable-field type because the server
 * returns them from the same route with the same 200.
 */
data class AuthResponse(
    val user: BridgeUser? = null,
    val token: String? = null,
    @SerializedName("requires_2fa") val requires2fa: Boolean? = null,
    @SerializedName("pending_token") val pendingToken: String? = null,
)

data class TwoFactorLoginRequest(
    @SerializedName("pending_token") val pendingToken: String,
    val code: String,
    @SerializedName("device_name") val deviceName: String? = null,
)

data class UpdateMeRequest(
    @SerializedName("display_name") val displayName: String? = null,
    /** Rate-limited server-side to twice per rolling 30 days -- see `bridgeDetail()`. */
    val username: String? = null,
)
