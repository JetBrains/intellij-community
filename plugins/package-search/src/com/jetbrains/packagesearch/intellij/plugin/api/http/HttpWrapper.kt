package com.jetbrains.packagesearch.intellij.plugin.api.http

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import java.net.HttpURLConnection

internal fun requestJsonObject(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int = 10,
    headers: List<Pair<String, String>>
): ApiResult<JsonObject> {
    val response = requestString(url, acceptContentType, timeoutInSeconds, headers)

    return response.mapSuccess { it.asJSONObject() }
}

@Suppress("TooGenericExceptionCaught") // Putting any potential issues in an Either.Left
private fun requestString(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int = 10,
    headers: List<Pair<String, String>>
): ApiResult<String> =
    try {
        val builder = HttpRequests.request(url)
            .productNameAsUserAgent()
            .accept(acceptContentType)
            .connectTimeout(timeoutInSeconds * 1000)
            .readTimeout(timeoutInSeconds * 1000)
            .tuner { connection ->
                headers.forEach {
                    connection.setRequestProperty(it.first, it.second)
                }
            }
        val statusCode = builder.tryConnect()
        val responseText = builder.readString()

        if (statusCode != HttpURLConnection.HTTP_OK) {
            Logger.getInstance("HttpWrapper").debug(
                """
                    |
                    |<-- HTTP GET $url
                    |    Accept: $acceptContentType
                    |${headers.joinToString("\n") { "    ${it.first}: ${it.second}" }}
                    |
                    |--> RESPONSE HTTP $statusCode
                    |$responseText
                    |
                """.trimMargin()
            )
        }

        when {
            responseText.isEmpty() -> ApiResult.Failure(EmptyBodyException())
            else -> ApiResult.Success(responseText)
        }
    } catch (t: Throwable) {
        t.log()
        ApiResult.Failure(t.log())
    }

private fun String.asJSONObject(): JsonObject = JsonParser.parseString(this).asJsonObject

private fun Throwable.log() = apply {
    @Suppress("TooGenericExceptionCaught") // Guarding against random runtime failures
    try {
        Logger.getInstance(this.javaClass).warn("Error occurred while performing a request", this)
    } catch (t: Throwable) {
        // IntelliJ logger rethrows logged exception
    }
}

internal class EmptyBodyException : RuntimeException(
    PackageSearchBundle.message("packagesearch.search.client.response.body.is.empty")
)
