package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.AuthResponse
import com.lumisound.android.bridge.model.BridgeUser
import com.lumisound.android.bridge.model.LoginRequest
import com.lumisound.android.bridge.model.RegisterRequest
import com.lumisound.android.bridge.model.TwoFactorLoginRequest
import com.lumisound.android.bridge.model.UpdateMeRequest
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

/**
 * The account surface every Lumisound client shares. Signing in here yields the
 * same 30-day session token iOS keeps in its Keychain, against the same
 * `ios_users` row -- which is what makes one account work on both apps.
 */
interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    /** Continuation of a login that came back `requires_2fa`. */
    @POST("auth/2fa/login")
    suspend fun twoFactorLogin(@Body body: TwoFactorLoginRequest): AuthResponse

    @GET("auth/me")
    suspend fun me(): BridgeUser

    @PUT("auth/me")
    suspend fun updateMe(@Body body: UpdateMeRequest): BridgeUser

    @POST("auth/logout")
    suspend fun logout()

    /** Raw JPEG or GIF bytes, max 15MB either way -- the server sniffs which. */
    @POST("user/avatar")
    suspend fun uploadAvatar(@Body body: RequestBody)
}
