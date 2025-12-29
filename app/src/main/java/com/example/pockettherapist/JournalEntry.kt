package com.example.pockettherapist

data class JournalEntry(
    val id: String = "",
    val title: String = "",
    val text: String = "",
    val mood: String = "",  // Optional mood emoji/tag
    val timestamp: Long = 0L
) {
    // No-argument constructor required for Firebase
    constructor() : this("", "", "", "", 0L)
}
