package circlet.ui

import com.intellij.openapi.util.*
import com.intellij.ui.*
import com.intellij.util.ui.*
import runtime.ui.*
import java.awt.*
import java.awt.geom.*
import java.awt.image.*
import javax.swing.*
import kotlin.math.*

object CircletAvatarUtils {
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

    @Suppress("UndesirableClassUsage")
    private fun generateColoredAvatar(name: String): BufferedImage {
        val (colorInt1, colorInt2) = Avatars.gradientInt(name)
        val (color1, color2) = Color(colorInt1) to Color(colorInt2)

        val shortName = Avatars.initials(name)
        val size = 128
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        applyQualityRenderingHints(g2)
        g2.paint = GradientPaint(0.0f, 0.0f, color2,
                                 size.toFloat(), size.toFloat(), color1)
        g2.fillRect(0, 0, size, size)
        g2.paint = JBColor.WHITE
        g2.font = JBFont.create(Font(if (SystemInfo.isWinVistaOrNewer) "Segoe UI" else "Tahoma", Font.PLAIN, (size / 2.2).toInt()))
        UIUtil.drawCenteredString(g2, Rectangle(0, 0, size, (size * 0.92).toInt()), shortName)
        g2.dispose()

        return image
    }

    fun createAvatars(image: BufferedImage): CircletAvatars {
        return CircletAvatars(
            JBImageIcon(buildCircleImage(image)),
            JBImageIcon(buildImageWithStatus(image, Color(224, 85, 85))),
            JBImageIcon(buildImageWithStatus(image, Color(98, 181, 67)))
        )
    }

    fun generateAvatars(name: String): CircletAvatars {
        val generatedImage = generateColoredAvatar(name)
        return createAvatars(generatedImage)
    }
}

data class CircletAvatars(
    val circle: Icon,
    val offline: Icon,
    val online: Icon
)
