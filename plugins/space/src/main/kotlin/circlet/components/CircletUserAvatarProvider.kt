package circlet.components

import circlet.platform.client.*
import circlet.ui.*
import circlet.utils.*
import icons.*
import kotlinx.coroutines.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.reactive.*
import javax.swing.*

class CircletUserAvatarProvider {
    private val log: KLogger = logger<CircletWorkspaceComponent>()

    private val avatarPlaceholder: Icon = CircletIcons.mainIcon

    private val lifetime: LifetimeSource = LifetimeSource()

    val avatar: Property<Icon> = lifetime.mapInit(circletWorkspace.workspace, avatarPlaceholder) { ws ->

        ws ?: return@mapInit avatarPlaceholder
        val avatarTID = ws.me.value.avatar ?: return@mapInit avatarPlaceholder
        val imageLoader = CircletImageLoader(ws.lifetime, ws.client)

        // await connected state before trying to load image.
        ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

        try {
            log.info { "loading user avatar: $avatarTID" }
            val image = imageLoader.loadImageAsync(avatarTID).await() ?: return@mapInit avatarPlaceholder
            CircleImageIcon(image)
        } catch (th: CancellationException) {
            throw th
        } catch (e: Exception) {
            log.error { "user avatar not loaded: $e" }
            avatarPlaceholder
        }

    }

    companion object {
        fun getInstance(): CircletUserAvatarProvider = application.getService(CircletUserAvatarProvider::class.java)
    }
}
