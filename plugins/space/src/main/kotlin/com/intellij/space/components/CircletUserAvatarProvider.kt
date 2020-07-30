package circlet.components

import circlet.client.api.englishFullName
import circlet.platform.client.ConnectionStatus
import circlet.ui.CircletAvatarUtils
import circlet.ui.CircletAvatars
import circlet.ui.CircletImageLoader
import circlet.utils.application
import kotlinx.coroutines.CancellationException
import libraries.coroutines.extra.LifetimeSource
import libraries.klogging.logger
import runtime.reactive.Property
import runtime.reactive.awaitFirst
import runtime.reactive.filter
import runtime.reactive.mapInit

class CircletUserAvatarProvider {
  private val log = logger<CircletUserAvatarProvider>()

  private val lifetime: LifetimeSource = LifetimeSource()

  private val avatarPlaceholders: CircletAvatars = CircletAvatars.MainIcon

  val avatars: Property<CircletAvatars> = lifetime.mapInit(space.workspace, avatarPlaceholders) { ws ->
    ws ?: return@mapInit avatarPlaceholders
    val id = ws.me.value.username
    val name = ws.me.value.englishFullName()

    val avatarTID = ws.me.value.smallAvatar ?: return@mapInit CircletAvatarUtils.generateAvatars(id, name)
    val imageLoader = CircletImageLoader(ws.lifetime, ws.client)

    // await connected state before trying to load image.
    ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

    try {
      log.info { "loading user avatar: $avatarTID" }
      val loadedImage = imageLoader.loadImageAsync(avatarTID).await()
      if (loadedImage == null) {
        CircletAvatarUtils.generateAvatars(id, name)
      }
      else {
        CircletAvatarUtils.createAvatars(loadedImage)
      }
    }
    catch (th: CancellationException) {
      throw th
    }
    catch (e: Exception) {
      log.error { "user avatar not loaded: $e" }
      avatarPlaceholders
    }
  }

  companion object {
    fun getInstance(): CircletUserAvatarProvider = application.getService(CircletUserAvatarProvider::class.java)
  }
}

