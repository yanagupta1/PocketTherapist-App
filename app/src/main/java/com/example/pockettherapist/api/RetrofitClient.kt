package com.example.pockettherapist.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    private const val OSM_BASE_URL = "https://overpass-api.de/api/"
    private const val TICKETMASTER_BASE_URL = "https://app.ticketmaster.com/discovery/v2/"

    val ticketmaster: TicketmasterApi by lazy {
        Retrofit.Builder()
            .baseUrl(TICKETMASTER_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TicketmasterApi::class.java)
    }

    val osm: OSMApi by lazy {
        Retrofit.Builder()
            .baseUrl(OSM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OSMApi::class.java)
    }
}
