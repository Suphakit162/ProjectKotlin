package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.host
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.port
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable

@Serializable
data class UrlMapping(
    val shortCode: String,
    val originalUrl: String,
    var clickCount: Int = 0
)

@Serializable
data class UrlRequest(
    val originalUrl: String
)

@Serializable
class UrlRepository {
    private val urlStorage = mutableMapOf<String, UrlMapping>()

    fun getByShortCode(code: String): UrlMapping? = urlStorage[code]
    fun save(mapping: UrlMapping) { urlStorage[mapping.shortCode] = mapping }
    fun clear() { urlStorage.clear() }
}

@Serializable
class UrlService(private val repo: UrlRepository) {
    fun getRedirectInfo(code: String): UrlMapping? {
        val mapping = repo.getByShortCode(code)
        mapping?.let {
            it.clickCount += 1
            repo.save(it) // Save updated mapping
        }
        return mapping
    }
}

val RepositoryKey = AttributeKey<UrlRepository>("Repository")

fun Application.configureUrlShortenerRoutes(repo: UrlRepository) {
    val urlService = UrlService(repo)

    routing {
        post {
            val req = call.receive<UrlRequest>()
            val code = generateShortCode()
            val mapping = UrlMapping(code, req.originalUrl)
            repo.save(mapping)
            call.respondText("http://${call.request.host()}:${call.request.port()}/$code", status = HttpStatusCode.Created)
        }

        get("/{shortCode}") {
            val code = call.parameters["shortCode"]
            if (code != null) {
                val mapping = urlService.getRedirectInfo(code)
                if (mapping != null) {
                    call.respondRedirect(mapping.originalUrl, permanent = false)
                } else {
                    call.respondText("Short URL not found", status = HttpStatusCode.NotFound)
                }
            } else {
                call.respondText("Short code is required", status = HttpStatusCode.BadRequest)
            }
        }
    }
}

fun generateShortCode(): String {
    val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    return List(6) { chars.random() }.joinToString("")
}