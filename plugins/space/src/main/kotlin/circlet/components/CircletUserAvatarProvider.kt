package circlet.components

import circlet.platform.client.*
import circlet.ui.*
import circlet.utils.*
import com.intellij.ui.*
import com.intellij.util.ui.*
import icons.*
import kotlinx.coroutines.*
import libraries.coroutines.extra.*
import libraries.klogging.*
import runtime.reactive.*
import java.awt.*
import java.awt.AlphaComposite
import java.awt.geom.*
import java.awt.image.*
import javax.swing.*
import kotlin.math.*


class CircletUserAvatarProvider {
    private val log: KLogger = logger<CircletWorkspaceComponent>()

    private val lifetime: LifetimeSource = LifetimeSource()

    private val avatarPlaceholders: CircletAvatars = CircletAvatars(
        CircletIcons.mainIcon,
        CircletIcons.mainIcon,
        CircletIcons.mainIcon
    )

    private val userAvatars = CircletAvatars(
        CircletIcons.user,
        CircletIcons.userOffline,
        CircletIcons.userOnline
    )

    val avatars: Property<CircletAvatars> = lifetime.mapInit(circletWorkspace.workspace, avatarPlaceholders) { ws ->
        ws ?: return@mapInit avatarPlaceholders
        val avatarTID = ws.me.value.smallAvatar ?: return@mapInit userAvatars
        val imageLoader = CircletImageLoader(ws.lifetime, ws.client)

        // await connected state before trying to load image.
        ws.client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

        try {
            log.info { "loading user avatar: $avatarTID" }
            val loadedImage = imageLoader.loadImageAsync(avatarTID).await()
            if (loadedImage == null) {
                userAvatars
            } else {
                CircletAvatars(
                    JBImageIcon(buildCircleImage(loadedImage)),
                    JBImageIcon(buildImageWithStatus(loadedImage, Color(224, 85, 85))),
                    JBImageIcon(buildImageWithStatus(loadedImage, Color(98, 181, 67)))
                )
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

data class CircletAvatars(
    val circle: Icon,
    val offline: Icon,
    val online: Icon
)

@Suppress("UndesirableClassUsage")
private fun buildCircleImage(image: BufferedImage): BufferedImage {
    val size: Int = min(image.width, image.height)
    val mask = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    var g2 = mask.createGraphics()
    applyQualityRenderingHints(g2)

    val avatarOvalArea = Area(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))
    g2.fill(avatarOvalArea)
    g2.dispose()

    val circleAvatar = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    g2 = circleAvatar.createGraphics()
    applyQualityRenderingHints(g2)
    g2.drawImage(image, 0, 0, null)
    g2.composite = AlphaComposite.getInstance(AlphaComposite.DST_IN)
    g2.drawImage(mask, 0, 0, null)
    g2.dispose()
    return circleAvatar
}

@Suppress("UndesirableClassUsage")
private fun buildImageWithStatus(image: BufferedImage, statusColor: Color): BufferedImage {
    val size: Int = min(image.width, image.height)
    val mask = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    var g2 = mask.createGraphics()
    applyQualityRenderingHints(g2)

    val outerD = size / 2.5
    val innerD = size / 3.5
    val greenSize = (size - (innerD + outerD) / 2).toInt()

    val avatarOvalArea = Area(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))
    val onlineOvalArea = Area(Ellipse2D.Double(greenSize.toDouble(), greenSize.toDouble(), outerD, outerD))
    avatarOvalArea.subtract(onlineOvalArea)
    g2.fill(avatarOvalArea)
    g2.dispose()

    val circleAvatar = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    g2 = circleAvatar.createGraphics()
    applyQualityRenderingHints(g2)

    g2.drawImage(image, 0, 0, null)
    val composite = g2.composite
    g2.composite = AlphaComposite.getInstance(AlphaComposite.DST_IN)
    g2.drawImage(mask, 0, 0, null)
    g2.composite = composite
    g2.paint = JBColor.PanelBackground
    g2.paint = statusColor
    g2.fillOval(size - innerD.toInt(), size - innerD.toInt(), innerD.toInt(), innerD.toInt())
    g2.dispose()
    return circleAvatar
}

private fun applyQualityRenderingHints(g2d: Graphics2D) {
    g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
    g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
}
