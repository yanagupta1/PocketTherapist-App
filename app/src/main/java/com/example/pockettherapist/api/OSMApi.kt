package com.example.pockettherapist.api

import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface OSMApi {

    @FormUrlEncoded
    @POST("interpreter")
    fun getAmenities(
        @Field("data") query: String
    ): Call<OSMResponse>
}
