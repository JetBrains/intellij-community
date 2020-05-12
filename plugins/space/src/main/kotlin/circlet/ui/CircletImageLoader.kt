package circlet.ui

import circlet.platform.api.*
import circlet.platform.api.oauth.*
import circlet.platform.client.*
import com.google.common.cache.*
import com.intellij.execution.process.*
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import libraries.coroutines.extra.*
import runtime.*
import runtime.async.*
import java.awt.image.*
import java.io.*
import java.util.concurrent.*
import javax.imageio.*

class CircletImageLoader(
    private val lifetime: Lifetime,
    client: KCircletClient
) {
    private val server: String = client.server.removeSuffix("/")
    private val tokenSource: TokenSource = client.tokenSource
    private val imagesEndpoint: String = "${server}/d"

    private val imageCache: Cache<TID, Deferred<BufferedImage?>> = CacheBuilder.newBuilder()
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build<TID, Deferred<BufferedImage?>>()

    private val coroutineDispatcher = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

    suspend fun loadImageAsync(imageTID: TID): Deferred<BufferedImage?> {
        return imageCache.get(imageTID) {
            async(lifetime, coroutineDispatcher) {
                backoff {
                    load(imageTID)
                }
            }
        }
    }

    private suspend fun load(imageTID: TID): BufferedImage? {
        val accessToken = tokenSource.token().accessToken
        return HttpClient().use { client ->
            val bytes = client.get<ByteArray>("$imagesEndpoint/$imageTID") {
                header("Authorization", "Bearer $accessToken")
            }
            ImageIO.read(ByteArrayInputStream(bytes))
        }
    }
}
