package com.lumisound.android.bridge

import android.os.Build
import android.util.Log
import com.lumisound.android.diagnostics.AppLogger
import com.lumisound.android.bridge.model.AddFavoriteRequest
import com.lumisound.android.bridge.model.BridgeUser
import com.lumisound.android.bridge.model.LoginRequest
import com.lumisound.android.bridge.model.RegisterRequest
import com.lumisound.android.bridge.model.TwoFactorLoginRequest
import com.lumisound.android.bridge.model.UpdateMeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the app knows about the signed-in account right now. */
sealed interface AccountState {
    data object Unknown : AccountState
    data object SignedOut : AccountState
    data class SignedIn(val user: BridgeUser) : AccountState
}

/** Outcome of a sign-in attempt -- 2FA is a first-class result, not an error. */
sealed interface SignInResult {
    data class Success(val user: BridgeUser) : SignInResult
    data class TwoFactorRequired(val pendingToken: String) : SignInResult
    data class Failure(val message: String) : SignInResult
}

/** Outcome of a `PUT /auth/me` field update (username, display name, ...). */
sealed interface UpdateResult {
    data object Success : UpdateResult
    data class Failure(val message: String) : UpdateResult
}

/**
 * Sign-in, session persistence and the account row -- against the same
 * `/auth` routes Lumisound uses, so the credentials a user already has work
 * here with no migration or linking step.
 */
class AccountRepository(
    private val http: BridgeHttp,
    private val tokenStore: TokenStore,
) {

    private val _state = MutableStateFlow<AccountState>(AccountState.Unknown)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = tokenStore.token != null

    /** The device label that shows up in the account's Sessions list on either client. */
    private val deviceName: String =
        listOfNotNull(Build.MANUFACTURER?.replaceFirstChar { it.uppercase() }, Build.MODEL)
            .joinToString(" ")
            .ifBlank { "Android device" }

    /**
     * Resolves the persisted token into a real account row, or clears it. Called
     * on launch: a 30-day session can have been revoked from another device, and
     * the only way to find out is to ask.
     */
    suspend fun restoreSession() {
        if (tokenStore.token == null) {
            _state.value = AccountState.SignedOut
            return
        }
        _state.value = try {
            AccountState.SignedIn(http.auth.me())
        } catch (e: Exception) {
            val revoked = (e as? retrofit2.HttpException)?.code() == 401
            Log.w(TAG, "session restore failed (revoked=$revoked): ${e.javaClass.simpleName}")
            if (revoked) {
                tokenStore.clear()
                AccountState.SignedOut
            } else {
                // A network blip is not a sign-out. Keep the token and let the next
                // authenticated call retry rather than dumping the user to sign-in
                // because their train went through a tunnel.
                AccountState.SignedOut
            }
        }
    }

    suspend fun signIn(username: String, password: String): SignInResult = runCatchingAuth {
        val startedAt = System.currentTimeMillis()
        val response = http.auth.login(LoginRequest(username.trim(), password, deviceName))
        // Measured at 9.4s on the first real device: the server hashes with bcrypt and
        // this is the app's slowest call by an order of magnitude. Logged so a slow sign-in
        // is identifiable as slow rather than reported as a hang.
        AppLogger.i("account", "login completed in ${System.currentTimeMillis() - startedAt}ms")
        when {
            response.requires2fa == true && response.pendingToken != null ->
                SignInResult.TwoFactorRequired(response.pendingToken)
            response.token != null && response.user != null -> {
                tokenStore.save(response.token)
                _state.value = AccountState.SignedIn(response.user)
                SignInResult.Success(response.user)
            }
            else -> SignInResult.Failure("The server returned a login response with no session in it.")
        }
    }

    suspend fun completeTwoFactor(pendingToken: String, code: String): SignInResult = runCatchingAuth {
        val response = http.auth.twoFactorLogin(
            TwoFactorLoginRequest(pendingToken, code.trim(), deviceName)
        )
        val token = response.token
        val user = response.user
        if (token == null || user == null) {
            SignInResult.Failure("That code was not accepted.")
        } else {
            tokenStore.save(token)
            _state.value = AccountState.SignedIn(user)
            SignInResult.Success(user)
        }
    }

    suspend fun register(username: String, email: String, password: String, displayName: String?): SignInResult =
        runCatchingAuth {
            val response = http.auth.register(
                RegisterRequest(username.trim(), password, email.trim(), displayName?.takeIf { it.isNotBlank() })
            )
            val token = response.token
            val user = response.user
            if (token == null || user == null) {
                SignInResult.Failure("Registration succeeded but no session came back. Try signing in.")
            } else {
                tokenStore.save(token)
                _state.value = AccountState.SignedIn(user)
                SignInResult.Success(user)
            }
        }

    suspend fun signOut() {
        try {
            http.auth.logout()
        } catch (e: Exception) {
            // The local token is dropped either way: a server that can't be reached
            // must not be able to keep someone signed in on their own device.
            Log.w(TAG, "logout call failed, clearing locally anyway: ${e.javaClass.simpleName}")
        }
        tokenStore.clear()
        _state.value = AccountState.SignedOut
    }

    /**
     * Changes the signed-in account's username. Rate-limited server-side to
     * twice per rolling 30 days (`ios_username_change_log`) -- the 429's
     * `detail` message already states exactly when the next change is
     * allowed, so it's surfaced verbatim via [bridgeDetail] rather than
     * replaced with a generic message here.
     */
    suspend fun updateUsername(newUsername: String): UpdateResult = try {
        val user = http.auth.updateMe(UpdateMeRequest(username = newUsername.trim()))
        _state.value = AccountState.SignedIn(user)
        UpdateResult.Success
    } catch (e: retrofit2.HttpException) {
        UpdateResult.Failure(e.bridgeDetail() ?: "The server rejected that (HTTP ${e.code()}).")
    } catch (e: Exception) {
        UpdateResult.Failure("Could not reach the bridge: ${e.message ?: e.javaClass.simpleName}")
    }

    suspend fun addFavorite(songId: String, title: String?, artist: String?, album: String?) {
        http.libraryData.addFavorite(AddFavoriteRequest(songId, title, artist, album))
    }

    suspend fun removeFavorite(songId: String) = http.libraryData.removeFavorite(songId)

    private inline fun runCatchingAuth(block: () -> SignInResult): SignInResult = try {
        block()
    } catch (e: retrofit2.HttpException) {
        SignInResult.Failure(e.bridgeDetail() ?: "The server rejected that (HTTP ${e.code()}).")
    } catch (e: Exception) {
        SignInResult.Failure("Could not reach the bridge: ${e.message ?: e.javaClass.simpleName}")
    }

    private companion object {
        const val TAG = "LumiAccount"
    }
}

/**
 * FastAPI puts the human-readable reason in a `detail` field -- "Username
 * already taken", "Invalid username or password", the password-strength rule.
 * Surfacing that beats showing a bare status code.
 */
fun retrofit2.HttpException.bridgeDetail(): String? = try {
    val body = response()?.errorBody()?.string() ?: return null
    Regex("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        .find(body)
        ?.groupValues
        ?.get(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\n", " ")
} catch (e: Exception) {
    null
}
