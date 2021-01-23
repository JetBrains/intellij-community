// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.components

import circlet.client.api.englishFullName
import circlet.platform.client.ConnectionStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.space.ui.SpaceAvatarUtils
import com.intellij.space.ui.SpaceAvatars
import com.intellij.space.ui.SpaceImageLoader
import kotlinx.coroutines.CancellationException
import libraries.coroutines.extra.LifetimeSource
import libraries.klogging.logger
import runtime.reactive.Property
import runtime.reactive.awaitFirst
import runtime.reactive.filter
import runtime.reactive.property.mapInit

@Service
class SpaceUserAvatarProvider : Disposable {
  companion object {
    private val LOG = logger<SpaceUserAvatarProvider>()

    fun getInstance(): SpaceUserAvatarProvider = service()
  }

  private val lifetime: LifetimeSource = LifetimeSource()

  private val avatarPlaceholders: SpaceAvatars = SpaceAvatars.MainIcon

  val avatars: Property<SpaceAvatars> = lifetime.mapInit(SpaceWorkspaceComponent.getInstance().workspace, avatarPlaceholders) { ws ->
    ws ?: return@mapInit avatarPlaceholders
    val id = ws.me.value.username
    val name = ws.me.value.englishFullName()

    val avatarTID = ws.me.value.smallAvatar ?: return@mapInit SpaceAvatarUtils.generateAvatars(id, name)
    val imageLoader = SpaceImageLoader.getInstance()

    // await connected state before trying to load image.
    ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

    try {
      LOG.info { "loading user avatar: $avatarTID" }
      val loadedImage = imageLoader.loadImageAsync(avatarTID)?.await()
      if (loadedImage == null) {
        SpaceAvatarUtils.generateAvatars(id, name)
      }
      else {
        SpaceAvatarUtils.createAvatars(loadedImage)
      }
    }
    catch (th: CancellationException) {
      throw th
    }
    catch (e: Exception) {
      LOG.error { "user avatar not loaded: $e" }
      avatarPlaceholders
    }
  }

  override fun dispose() {
    lifetime.terminate()
  }
}

