package com.example.pockettherapist.api

data class TicketmasterResponse(
    val _embedded: EmbeddedEvents?
)

data class EmbeddedEvents(
    val events: List<TMEvent>?
)

data class TMEvent(
    val name: String?,
    val url: String?,
    val dates: TMDates?,
    val _embedded: TmEmbeddedVenues?
)

data class TMDates(
    val start: TMStart?
)

data class TMStart(
    val localDate: String?,
    val localTime: String?
)

data class TmEmbeddedVenues(
    val venues: List<TMVenue>?
)

data class TMVenue(
    val name: String?
)
