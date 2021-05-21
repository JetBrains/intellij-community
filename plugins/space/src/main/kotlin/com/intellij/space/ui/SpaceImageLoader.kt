// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.ui

import circlet.platform.api.TID
import circlet.platform.api.oauth.TokenSource
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.space.components.SpaceWorkspaceComponent
import com.intellij.space.utils.LifetimedDisposable
import com.intellij.space.utils.LifetimedDisposableImpl
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import libraries.coroutines.extra.async
import runtime.async.backoff
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

@Service
class SpaceImageLoader: LifetimedDisposable by LifetimedDisposableImpl() {
  private val coroutineDispatcher: ExecutorCoroutineDispatcher = ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()

  private val imageCache: Cache<TID, Deferred<BufferedImage?>> = CacheBuilder.newBuilder()
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build()


  init {
    lifetime.add {
      imageCache.cleanUp()
    }
    SpaceWorkspaceComponent.getInstance().workspace.forEach(lifetime) {
      imageCache.cleanUp()
    }
  }

  suspend fun loadImageAsync(imageTID: TID): Deferred<BufferedImage?>? {
    val kCircletClient = SpaceWorkspaceComponent.getInstance().workspace.value?.client ?: return null

    val server: String = kCircletClient.server.removeSuffix("/")
    val imagesEndpoint = "${server}/d"
    val tokenSource: TokenSource = kCircletClient.tokenSource

    return imageCache.get(imageTID) {
      async(kCircletClient.lifetime, coroutineDispatcher) {
        backoff {
          load(imageTID, tokenSource, imagesEndpoint)
        }
      }
    }
  }

  private suspend fun load(imageTID: TID,
                           tokenSource: TokenSource,
                           imagesEndpoint: String): BufferedImage? {
    val accessToken = tokenSource.token().accessToken
    return HttpClient().use { client ->
      val bytes = client.get<ByteArray>("$imagesEndpoint/$imageTID") {
        header("Authorization", "Bearer $accessToken")
      }
      ImageIO.read(ByteArrayInputStream(bytes))
    }
  }

  companion object {
    fun getInstance(): SpaceImageLoader = service()
  }
}
