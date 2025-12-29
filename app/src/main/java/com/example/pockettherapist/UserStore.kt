package com.example.pockettherapist

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object UserStore {
    private val database = FirebaseDatabase.getInstance()
    private val usersRef = database.getReference("users")

    private const val PREFS_NAME = "pocket_therapist_prefs"
    private const val PROFILE_PICS_PREFS = "profile_pictures_prefs"
    private const val KEY_USERNAME = "logged_in_username"
    private const val KEY_AGE = "user_age"
    private const val KEY_GENDER = "user_gender"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_BIO = "user_bio"
    private const val KEY_BIRTHDATE = "user_birthdate"
    private const val KEY_LOCATION = "user_location"
    private const val KEY_INTERESTS = "user_interests"
    private const val KEY_PROFILE_PICTURE_URL = "profile_picture_url"

    private var prefs: SharedPreferences? = null
    private var profilePicsPrefs: SharedPreferences? = null
    private var appContext: Context? = null

    var loggedInUser: String? = null
    var age: String? = null
    var gender: String? = null
    var displayName: String? = null
    var bio: String? = null
    var birthdate: String? = null
    var location: String? = null
    var interests: String? = null
    var profilePictureUrl: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        profilePicsPrefs = context.getSharedPreferences(PROFILE_PICS_PREFS, Context.MODE_PRIVATE)
        // Restore saved session
        loggedInUser = prefs?.getString(KEY_USERNAME, null)
        age = prefs?.getString(KEY_AGE, null)
        gender = prefs?.getString(KEY_GENDER, null)
        displayName = prefs?.getString(KEY_DISPLAY_NAME, null)
        bio = prefs?.getString(KEY_BIO, null)
        birthdate = prefs?.getString(KEY_BIRTHDATE, null)
        location = prefs?.getString(KEY_LOCATION, null)
        interests = prefs?.getString(KEY_INTERESTS, null)
        // Load profile picture path from username mapping
        loggedInUser?.let { username ->
            profilePictureUrl = profilePicsPrefs?.getString(username, null)
        }
    }

    fun isLoggedIn(): Boolean = loggedInUser != null

    private fun saveSession() {
        prefs?.edit()?.apply {
            putString(KEY_USERNAME, loggedInUser)
            putString(KEY_AGE, age)
            putString(KEY_GENDER, gender)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_BIO, bio)
            putString(KEY_BIRTHDATE, birthdate)
            putString(KEY_LOCATION, location)
            putString(KEY_INTERESTS, interests)
            putString(KEY_PROFILE_PICTURE_URL, profilePictureUrl)
            apply()
        }
    }

    private fun clearSession() {
        prefs?.edit()?.clear()?.apply()
    }

    fun signUp(
        username: String,
        password: String,
        fullName: String,
        location: String,
        age: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        usersRef.child(username).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    onFailure("Username already taken")
                } else {
                    val userData = mapOf(
                        "password" to password,
                        "displayName" to fullName,
                        "location" to location,
                        "age" to age
                    )
                    usersRef.child(username).setValue(userData)
                        .addOnSuccessListener {
                            loggedInUser = username
                            this@UserStore.displayName = fullName
                            this@UserStore.location = location
                            this@UserStore.age = age
                            saveSession()
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            onFailure(e.message ?: "Sign up failed")
                        }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.message)
            }
        })
    }

    fun signIn(
        username: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        usersRef.child(username).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onFailure("User not found")
                    return
                }

                val storedPassword = snapshot.child("password").getValue(String::class.java)
                if (storedPassword == password) {
                    loggedInUser = username
                    age = snapshot.child("age").getValue(String::class.java)
                    gender = snapshot.child("gender").getValue(String::class.java)
                    displayName = snapshot.child("displayName").getValue(String::class.java)
                    bio = snapshot.child("bio").getValue(String::class.java)
                    birthdate = snapshot.child("birthdate").getValue(String::class.java)
                    location = snapshot.child("location").getValue(String::class.java)
                    interests = snapshot.child("interests").getValue(String::class.java)
                    // Load profile picture from local storage mapping
                    profilePictureUrl = profilePicsPrefs?.getString(username, null)
                    saveSession()
                    onSuccess()
                } else {
                    onFailure("Incorrect password")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.message)
            }
        })
    }

    fun updateProfile(
        age: String,
        gender: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        val updates = mapOf(
            "age" to age,
            "gender" to gender
        )

        usersRef.child(username).updateChildren(updates)
            .addOnSuccessListener {
                this@UserStore.age = age
                this@UserStore.gender = gender
                saveSession()
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Update failed")
            }
    }

    fun signOut() {
        loggedInUser = null
        age = null
        gender = null
        displayName = null
        bio = null
        birthdate = null
        location = null
        interests = null
        profilePictureUrl = null
        clearSession()
    }

    // Full Profile Operations

    fun updateFullProfile(
        displayName: String,
        bio: String,
        age: String,
        gender: String,
        birthdate: String,
        location: String,
        interests: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        val updates = mapOf(
            "displayName" to displayName,
            "bio" to bio,
            "age" to age,
            "gender" to gender,
            "birthdate" to birthdate,
            "location" to location,
            "interests" to interests
        )

        usersRef.child(username).updateChildren(updates)
            .addOnSuccessListener {
                this@UserStore.displayName = displayName
                this@UserStore.bio = bio
                this@UserStore.age = age
                this@UserStore.gender = gender
                this@UserStore.birthdate = birthdate
                this@UserStore.location = location
                this@UserStore.interests = interests
                saveSession()
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Update failed")
            }
    }

    fun uploadProfilePicture(
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        val context = appContext ?: run {
            onFailure("App not initialized")
            return
        }

        try {
            // Create profile pictures directory
            val profilePicsDir = File(context.filesDir, "profile_pictures")
            if (!profilePicsDir.exists()) {
                profilePicsDir.mkdirs()
            }

            // Create file for this user's profile picture
            val profilePicFile = File(profilePicsDir, "$username.jpg")

            // Copy image from URI to local file
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                FileOutputStream(profilePicFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: run {
                onFailure("Could not read image")
                return
            }

            // Store the local file path
            val localPath = profilePicFile.absolutePath

            // Save mapping of username -> local path
            profilePicsPrefs?.edit()?.putString(username, localPath)?.apply()

            // Update local state
            profilePictureUrl = localPath
            saveSession()

            onSuccess(localPath)
        } catch (e: Exception) {
            onFailure(e.message ?: "Failed to save profile picture")
        }
    }

    /**
     * Get the profile picture path for a specific username
     */
    fun getProfilePicturePath(username: String): String? {
        return profilePicsPrefs?.getString(username, null)
    }

    fun loadProfile(
        onSuccess: (UserProfile) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        usersRef.child(username).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get profile picture from local storage mapping
                val localProfilePicPath = profilePicsPrefs?.getString(username, null) ?: ""

                val profile = UserProfile(
                    username = username,
                    displayName = snapshot.child("displayName").getValue(String::class.java) ?: "",
                    bio = snapshot.child("bio").getValue(String::class.java) ?: "",
                    age = snapshot.child("age").getValue(String::class.java) ?: "",
                    gender = snapshot.child("gender").getValue(String::class.java) ?: "",
                    birthdate = snapshot.child("birthdate").getValue(String::class.java) ?: "",
                    location = snapshot.child("location").getValue(String::class.java) ?: "",
                    interests = snapshot.child("interests").getValue(String::class.java) ?: "",
                    profilePictureUrl = localProfilePicPath
                )
                // Update local cache
                displayName = profile.displayName
                bio = profile.bio
                age = profile.age
                gender = profile.gender
                birthdate = profile.birthdate
                location = profile.location
                interests = profile.interests
                profilePictureUrl = localProfilePicPath
                saveSession()
                onSuccess(profile)
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.message)
            }
        })
    }

    // Journal Entry Operations

    fun saveJournalEntry(
        title: String,
        text: String,
        mood: String = "",
        onSuccess: (JournalEntry) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        val journalsRef = usersRef.child(username).child("journals")
        val newEntryRef = journalsRef.push()
        val entryId = newEntryRef.key ?: run {
            onFailure("Failed to generate entry ID")
            return
        }

        val timestamp = System.currentTimeMillis()
        val entry = JournalEntry(
            id = entryId,
            title = title,
            text = text,
            mood = mood,
            timestamp = timestamp
        )

        val entryData = mapOf(
            "title" to title,
            "text" to text,
            "mood" to mood,
            "timestamp" to timestamp
        )

        newEntryRef.setValue(entryData)
            .addOnSuccessListener {
                onSuccess(entry)
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Failed to save journal entry")
            }
    }

    fun loadJournalEntries(
        onSuccess: (List<JournalEntry>) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        val journalsRef = usersRef.child(username).child("journals")
        journalsRef.orderByChild("timestamp").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = mutableListOf<JournalEntry>()
                for (childSnapshot in snapshot.children) {
                    val id = childSnapshot.key ?: continue
                    val title = childSnapshot.child("title").getValue(String::class.java) ?: ""
                    val text = childSnapshot.child("text").getValue(String::class.java) ?: ""
                    val mood = childSnapshot.child("mood").getValue(String::class.java) ?: ""
                    val timestamp = childSnapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                    entries.add(JournalEntry(id, title, text, mood, timestamp))
                }
                // Sort by timestamp descending (newest first)
                entries.sortByDescending { it.timestamp }
                onSuccess(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.message)
            }
        })
    }

    fun deleteJournalEntry(
        entryId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        usersRef.child(username).child("journals").child(entryId).removeValue()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onFailure(e.message ?: "Failed to delete journal entry")
            }
    }

    fun updateJournalEntry(
        entryId: String,
        newTitle: String,
        newText: String,
        newMood: String = "",
        onSuccess: (JournalEntry) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val username = loggedInUser ?: run {
            onFailure("No user logged in")
            return
        }

        val journalRef = usersRef.child(username).child("journals").child(entryId)

        // Get existing timestamp then update
        journalRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                val updates = mapOf(
                    "title" to newTitle,
                    "text" to newText,
                    "mood" to newMood,
                    "timestamp" to timestamp
                )

                journalRef.updateChildren(updates)
                    .addOnSuccessListener {
                        val updatedEntry = JournalEntry(entryId, newTitle, newText, newMood, timestamp)
                        onSuccess(updatedEntry)
                    }
                    .addOnFailureListener { e ->
                        onFailure(e.message ?: "Failed to update journal entry")
                    }
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure(error.message)
            }
        })
    }

    fun setCurrentUsername(username: String) {
        loggedInUser = username
        prefs?.edit()?.putString(KEY_USERNAME, username)?.apply()
    }

    fun getCurrentUsername(): String {
        return prefs?.getString(KEY_USERNAME, "") ?: ""
    }



}
