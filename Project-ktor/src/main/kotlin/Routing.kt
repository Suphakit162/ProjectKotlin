package com.example

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap


@Serializable
data class ShortenRequest(val longUrl: String)

@Serializable
data class ShortenResponse(val shortUrl: String)

@Serializable
data class UrlStats(val longUrl: String, val clicks: Int)

private data class UrlEntry(val longUrl: String, var clicks: Int = 0)


object UrlShortener {
    private val urlStore = ConcurrentHashMap<String, UrlEntry>()
    private const val baseUrl = "http://localhost:8080/"

    fun shortenUrl(longUrl: String): String {
        val existing = urlStore.entries.firstOrNull { it.value.longUrl == longUrl }
        if (existing != null) return baseUrl + existing.key

        var code: String
        do {
            code = generateShortCode()
        } while (urlStore.containsKey(code))

        urlStore[code] = UrlEntry(longUrl)
        return baseUrl + code
    }

    fun getLongUrlAndIncrementClicks(code: String): String? {
        val entry = urlStore[code] ?: return null
        entry.clicks++
        return entry.longUrl
    }

    fun getStats(code: String): UrlStats? {
        val entry = urlStore[code] ?: return null
        return UrlStats(entry.longUrl, entry.clicks)
    }

    private fun generateShortCode(length: Int = 6): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}


fun Application.configureRouting() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        post("/shorten") {
            try {
                val rawBody = call.receiveText()
                println(">>> Raw body: $rawBody")

                val request = Json.decodeFromString<ShortenRequest>(rawBody)
                println("Parsed longUrl: ${request.longUrl}")

                if (!request.longUrl.startsWith("http")) {
                    call.respondText("Invalid URL", status = HttpStatusCode.BadRequest)
                    return@post
                }

                val shortUrl = UrlShortener.shortenUrl(request.longUrl)
                call.respond(ShortenResponse(shortUrl))

            } catch (e: Exception) {
                e.printStackTrace()
                call.respondText("Error: ${e.message}", status = HttpStatusCode.BadRequest)
            }
        }

        get("/{code}") {
            val code = call.parameters["code"] ?: return@get call.respondText(
                "Missing code",
                status = HttpStatusCode.BadRequest
            )

            val longUrl = UrlShortener.getLongUrlAndIncrementClicks(code)
            if (longUrl != null) {
                call.respondRedirect(longUrl, permanent = false)
            } else {
                call.respondText("Short URL not found", status = HttpStatusCode.NotFound)
            }
        }

        get("/stats/{code}") {
            val code = call.parameters["code"] ?: return@get call.respondText(
                "Missing code",
                status = HttpStatusCode.BadRequest
            )

            val stats = UrlShortener.getStats(code)
            if (stats != null) {
                call.respond(stats)
            } else {
                call.respondText("Not found", status = HttpStatusCode.NotFound)
            }
        }
    }
}
