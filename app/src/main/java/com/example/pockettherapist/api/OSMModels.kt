package com.example.pockettherapist.api

data class OSMResponse(
    val elements: List<OSMElement>
)

data class OSMElement(
    val type: String,
    val lat: Double?,
    val lon: Double?,
    val tags: Map<String, String>?
)
