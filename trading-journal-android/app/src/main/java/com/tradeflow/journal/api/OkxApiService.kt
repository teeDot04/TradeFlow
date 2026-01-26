package com.tradeflow.journal.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface OkxApiService {
    @GET("/api/v5/market/candles")
    suspend fun getCandles(
        @Query("instId") instId: String,
        @Query("bar") bar: String = "1H",
        @Query("after") after: Long? = null,
        @Query("before") before: Long? = null,
        @Query("limit") limit: Int = 100
    ): OkxCandleResponse

    companion object {
        private const val BASE_URL = "https://www.okx.com"

        fun create(): OkxApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OkxApiService::class.java)
        }
    }
}

data class OkxCandleResponse(
    val code: String,
    val msg: String,
    val data: List<List<String>>
)
