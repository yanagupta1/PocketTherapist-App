package com.example.pockettherapist

data class UserProfile(
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val age: String = "",
    val gender: String = "",
    val birthdate: String = "",
    val location: String = "",
    val interests: String = "",
    val profilePictureUrl: String = ""
) {
    // No-argument constructor required for Firebase
    constructor() : this("", "", "", "", "", "", "", "", "")
}
