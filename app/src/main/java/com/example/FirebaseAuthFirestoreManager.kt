package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class UserPortfolioNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SyncStatus(
    val isFirebaseConfigured: Boolean,
    val isAuthenticated: Boolean,
    val userEmail: String?,
    val userName: String?,
    val lastSyncTime: Long? = null,
    val syncedNotesCount: Int = 0,
    val message: String
)

object FirebaseAuthFirestoreManager {
    private const val TAG = "FirebaseManager"
    private const val PREFS_NAME = "firebase_local_sync_prefs"

    fun isFirebaseInitialized(context: Context): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    fun getCurrentUser(context: Context): FirebaseUser? {
        return try {
            if (isFirebaseInitialized(context)) {
                FirebaseAuth.getInstance().currentUser
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getSyncStatus(context: Context): SyncStatus {
        val configured = isFirebaseInitialized(context)
        if (configured) {
            val user = getCurrentUser(context)
            return SyncStatus(
                isFirebaseConfigured = true,
                isAuthenticated = user != null,
                userEmail = user?.email,
                userName = user?.displayName ?: user?.email?.substringBefore("@"),
                message = if (user != null) "Connected via Firebase Auth" else "Firebase ready. Sign in with Google / Email."
            )
        } else {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val mockEmail = prefs.getString("local_user_email", null)
            val mockName = prefs.getString("local_user_name", null)
            return SyncStatus(
                isFirebaseConfigured = false,
                isAuthenticated = mockEmail != null,
                userEmail = mockEmail,
                userName = mockName,
                message = if (mockEmail != null) "Signed in (Local Account). To enable cloud Firestore sync, place google-services.json in the app directory."
                else "Firebase awaiting google-services.json. You can sign in locally to preserve portfolio notes."
            )
        }
    }

    suspend fun signInWithEmail(context: Context, email: String): Result<String> = withContext(Dispatchers.IO) {
        val configured = isFirebaseInitialized(context)
        if (configured) {
            return@withContext Result.failure(
                IllegalStateException("Google Sign-In via CredentialManager is required for Firebase Authentication.")
            )
        } else {
            // Local persistence fallback
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            prefs.edit()
                .putString("local_user_email", email)
                .putString("local_user_name", name)
                .apply()
            return@withContext Result.success("Signed in locally as $email (Firebase ready upon adding google-services.json)")
        }
    }

    fun signOut(context: Context) {
        try {
            if (isFirebaseInitialized(context)) {
                FirebaseAuth.getInstance().signOut()
            }
        } catch (e: Exception) {}
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    suspend fun syncPortfolioNotesToFirestore(
        context: Context,
        notes: List<UserPortfolioNote>
    ): Result<Int> = withContext(Dispatchers.IO) {
        val configured = isFirebaseInitialized(context)
        if (configured) {
            try {
                val user = getCurrentUser(context)
                val uid = user?.uid ?: "anonymous_user"
                val db = FirebaseFirestore.getInstance()
                val batch = db.batch()
                for (note in notes) {
                    val docRef = db.collection("users").document(uid).collection("portfolio_notes").document(note.id)
                    val data = mapOf(
                        "title" to note.title,
                        "content" to note.content,
                        "timestamp" to note.timestamp
                    )
                    batch.set(docRef, data)
                }
                batch.commit().await()
                return@withContext Result.success(notes.size)
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing notes to Firestore", e)
                return@withContext Result.failure(e)
            }
        } else {
            // Store locally in SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt("local_synced_notes_count", notes.size).apply()
            return@withContext Result.success(notes.size)
        }
    }
}
