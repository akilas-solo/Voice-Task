package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class TgApiResponse<T>(
    val ok: Boolean = false,
    val result: T? = null,
    @Json(name = "error_code") val errorCode: Int? = null,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class TgUser(
    val id: Long,
    @Json(name = "is_bot") val isBot: Boolean = false,
    @Json(name = "first_name") val firstName: String = "",
    val username: String? = null
)

@JsonClass(generateAdapter = true)
data class TgChat(
    val id: Long,
    val type: String = "private",
    val username: String? = null,
    @Json(name = "first_name") val firstName: String? = null
)

@JsonClass(generateAdapter = true)
data class TgMessage(
    @Json(name = "message_id") val messageId: Long,
    val from: TgUser? = null,
    val chat: TgChat,
    val date: Long = 0L,
    val text: String? = null,
    val caption: String? = null
)

@JsonClass(generateAdapter = true)
data class TgCallbackQuery(
    val id: String,
    val from: TgUser,
    val message: TgMessage? = null,
    val data: String? = null
)

@JsonClass(generateAdapter = true)
data class TgUpdate(
    @Json(name = "update_id") val updateId: Long,
    val message: TgMessage? = null,
    @Json(name = "callback_query") val callbackQuery: TgCallbackQuery? = null
)

@JsonClass(generateAdapter = true)
data class TgInlineKeyboardButton(
    val text: String,
    @Json(name = "callback_data") val callbackData: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class TgInlineKeyboardMarkup(
    @Json(name = "inline_keyboard") val inlineKeyboard: List<List<TgInlineKeyboardButton>>
)

@JsonClass(generateAdapter = true)
data class TgSendMessageRequest(
    @Json(name = "chat_id") val chatId: Long,
    val text: String,
    @Json(name = "parse_mode") val parseMode: String = "HTML",
    @Json(name = "reply_markup") val replyMarkup: TgInlineKeyboardMarkup? = null
)

@JsonClass(generateAdapter = true)
data class TgAnswerCallbackRequest(
    @Json(name = "callback_query_id") val callbackQueryId: String,
    val text: String? = null,
    @Json(name = "show_alert") val showAlert: Boolean = false
)

@JsonClass(generateAdapter = true)
data class TgEditReplyMarkupRequest(
    @Json(name = "chat_id") val chatId: Long,
    @Json(name = "message_id") val messageId: Long,
    @Json(name = "reply_markup") val replyMarkup: TgInlineKeyboardMarkup? = null
)

interface TelegramBotApi {
    @GET("bot{token}/getMe")
    suspend fun getMe(@Path("token") token: String): Response<TgApiResponse<TgUser>>

    @GET("bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token") token: String,
        @Query("offset") offset: Long? = null,
        @Query("limit") limit: Int = 50,
        @Query("timeout") timeout: Int = 0
    ): Response<TgApiResponse<List<TgUpdate>>>

    @POST("bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token") token: String,
        @Body request: TgSendMessageRequest
    ): Response<TgApiResponse<TgMessage>>

    @Multipart
    @POST("bot{token}/sendAudio")
    suspend fun sendAudio(
        @Path("token") token: String,
        @Part("chat_id") chatId: RequestBody,
        @Part audio: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
        @Part("parse_mode") parseMode: RequestBody,
        @Part("title") title: RequestBody,
        @Part("performer") performer: RequestBody,
        @Part("duration") durationSeconds: RequestBody,
        @Part("reply_markup") replyMarkup: RequestBody? = null
    ): Response<TgApiResponse<TgMessage>>

    @POST("bot{token}/answerCallbackQuery")
    suspend fun answerCallbackQuery(
        @Path("token") token: String,
        @Body request: TgAnswerCallbackRequest
    ): Response<TgApiResponse<Boolean>>

    @POST("bot{token}/editMessageReplyMarkup")
    suspend fun editMessageReplyMarkup(
        @Path("token") token: String,
        @Body request: TgEditReplyMarkupRequest
    ): Response<TgApiResponse<TgMessage>>
}

object TelegramApiClient {
    val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val api: TelegramBotApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.telegram.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(TelegramBotApi::class.java)
    }
}
