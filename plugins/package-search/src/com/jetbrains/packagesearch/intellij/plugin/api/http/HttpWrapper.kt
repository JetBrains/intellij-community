package com.jetbrains.packagesearch.intellij.plugin.api.http

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
import kotlin.coroutines.resumeWithException

internal suspend fun requestString(
    url: String,
    acceptContentType: String,
    timeoutInSeconds: Int = 10,
    headers: List<Pair<String, String>>
): String = suspendCancellableCoroutine { cont ->
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

            when {
                responseText.isEmpty() -> cont.resumeWithException(EmptyBodyException())
                else -> cont.resume(responseText)
            }
        }
    } catch (t: Throwable) {
        cont.resumeWithException(t)
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
