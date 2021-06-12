package com.jetbrains.packagesearch.intellij.plugin.api.http

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.castSafelyTo
import com.intellij.util.io.HttpRequests
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import kotlin.coroutines.resume

@Suppress("TooGenericExceptionCaught") // Putting any potential issues in an Either.Left
internal suspend fun requestString(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int = 10,
    headers: List<Pair<String, String>>
): ApiResult<String> = suspendCancellableCoroutine { cont ->
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
        builder.connect { request ->
            val statusCode = request.connection.castSafelyTo<HttpURLConnection>()?.responseCode ?: -1
            val responseText = request.connection.getInputStream().use { it.readBytes { cont.isCancelled }.toString(Charsets.UTF_8) }
            if (cont.isCancelled) return@connect
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

            val r = when {
                responseText.isEmpty() -> ApiResult.Failure(EmptyBodyException())
                else -> ApiResult.Success(responseText)
            }
            cont.resume(r)
        }
    } catch (t: Throwable) {
        t.log()
        cont.resume(ApiResult.Failure(t.log()))
    }
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

private fun InputStream.copyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE, cancellationRequested: () -> Boolean): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0 && !cancellationRequested()) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        bytes = read(buffer)
    }
    return bytesCopied
}


private fun InputStream.readBytes(cancellationRequested: () -> Boolean): ByteArray {
    val buffer = ByteArrayOutputStream(maxOf(DEFAULT_BUFFER_SIZE, this.available()))
    copyTo(buffer, cancellationRequested = cancellationRequested)
    return buffer.toByteArray()
}
