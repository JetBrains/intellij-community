package circlet.components

import circlet.client.api.*
import circlet.platform.client.*
import circlet.ui.*
import circlet.utils.*
import icons.*
import kotlinx.coroutines.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.reactive.*

class CircletUserAvatarProvider {
    private val log: KLogger = logger<CircletWorkspaceComponent>()

    private val lifetime: LifetimeSource = LifetimeSource()

    private val avatarPlaceholders: CircletAvatars = CircletAvatars(
        CircletIcons.mainIcon,
        CircletIcons.mainIcon,
        CircletIcons.mainIcon
    )

    val avatars: Property<CircletAvatars> = lifetime.mapInit(circletWorkspace.workspace, avatarPlaceholders) { ws ->
        ws ?: return@mapInit avatarPlaceholders
        val avatarTID = ws.me.value.smallAvatar ?: return@mapInit CircletAvatarUtils.generateAvatars(ws.me.value.englishFullName())
        val imageLoader = CircletImageLoader(ws.lifetime, ws.client)

        // await connected state before trying to load image.
        ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

        try {
            log.info { "loading user avatar: $avatarTID" }
            val loadedImage = imageLoader.loadImageAsync(avatarTID).await()
            if (loadedImage == null) {
                CircletAvatarUtils.generateAvatars(ws.me.value.englishFullName())
            }
            else {
                CircletAvatarUtils.createAvatars(loadedImage)
            }
        } catch (th: CancellationException) {
            throw th
        } catch (e: Exception) {
            log.error { "user avatar not loaded: $e" }
            avatarPlaceholders
        }
    }

    companion object {
        fun getInstance(): CircletUserAvatarProvider = application.getService(CircletUserAvatarProvider::class.java)
    }
}

