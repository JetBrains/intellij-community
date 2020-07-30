package com.intellij.space.ui

import circlet.platform.api.TID
import circlet.platform.api.oauth.TokenSource
import circlet.platform.client.KCircletClient
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.execution.process.ProcessIOExecutorService
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.Lifetime
import libraries.coroutines.extra.async
import runtime.async.backoff
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

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
