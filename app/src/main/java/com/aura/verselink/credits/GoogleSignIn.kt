package com.aura.verselink.credits

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * "Sign in with Google" via Credential Manager, requesting an ID token
 * whose audience is the credits backend's web client ID (that is what the
 * backend verifies at /auth/google).
 *
 * [context] must be an Activity context — Credential Manager shows UI.
 * Returns the raw Google ID token; the token is never logged.
 */
object GoogleSignIn {

    suspend fun requestIdToken(context: Context): String {
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            // false: show all Google accounts, not only previously authorized
            // ones — first-time users must be able to pick their account.
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(CreditsConfig.WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = try {
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialCancellationException) {
            throw CreditsException.SignInFailed("Sign-in was cancelled")
        } catch (e: GetCredentialException) {
            throw CreditsException.SignInFailed("Google sign-in failed: ${e.message}", e)
        }

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw CreditsException.SignInFailed("Google sign-in returned an unexpected credential type")
    }
}
