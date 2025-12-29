package com.example.pockettherapist.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface TicketmasterApi {

    @GET("events.json")
    fun getEvents(
        @Query("apikey") apiKey: String,
        @Query("latlong") latlong: String,
        @Query("radius") radius: Int = 10,
        @Query("size") size: Int = 20
    ): Call<TicketmasterResponse>
}
