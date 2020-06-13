@file:Suppress("UndesirableClassUsage")

package circlet.ui

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.*
import icons.SpaceIcons
import runtime.ui.Avatars
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.min

object CircletAvatarUtils {
    private fun createImageByMask(image: BufferedImage, area: Area): BufferedImage {
        val size: Int = min(image.width, image.height)
        val mask = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        var g2 = mask.createGraphics()
        applyQualityRenderingHints(g2)
        g2.fill(area)

        val shapedImage = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        g2 = shapedImage.createGraphics()
        applyQualityRenderingHints(g2)
        g2.drawImage(image, 0, 0, null)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.DST_IN)
        g2.drawImage(mask, 0, 0, null)
        g2.dispose()

        return shapedImage
    }

    internal fun createCircleImage(image: BufferedImage): BufferedImage {
        val size: Int = min(image.width, image.height)
        val avatarOvalArea = Area(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))

        return createImageByMask(image, avatarOvalArea)
    }

    @Suppress("unused")
    internal fun createRoundedImage(image: BufferedImage): BufferedImage {
        val size: Int = min(image.width, image.height)
        val arc = size / 4.0
        val avatarOvalArea = Area(RoundRectangle2D.Double(0.0, 0.0, size.toDouble(), size.toDouble(), arc, arc))

        return createImageByMask(image, avatarOvalArea)
    }

    internal fun buildImageWithStatus(image: BufferedImage, statusColor: Color): BufferedImage {
        val size: Int = min(image.width, image.height)

        val outerD = size / 2.5
        val innerD = size / 3.5
        val greenSize = (size - (innerD + outerD) / 2).toInt()

        val avatarOvalArea = Area(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))
        val onlineOvalArea = Area(Ellipse2D.Double(greenSize.toDouble(), greenSize.toDouble(), outerD, outerD))
        avatarOvalArea.subtract(onlineOvalArea)

        val circleAvatar = createImageByMask(image, avatarOvalArea)
        val g2 = circleAvatar.createGraphics()
        applyQualityRenderingHints(g2)
        g2.paint = statusColor
        g2.fillOval(size - innerD.toInt(), size - innerD.toInt(), innerD.toInt(), innerD.toInt())
        g2.dispose()
        return circleAvatar
    }

    private fun applyQualityRenderingHints(g2d: Graphics2D) {
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
    }

    internal fun generateColoredAvatar(gradientSeed: String, name: String): BufferedImage {
        val (colorInt1, colorInt2) = Avatars.gradientInt(gradientSeed)
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
        return CircletAvatars.Image(image)
    }

    fun generateAvatars(gradientSeed: String, name: String): CircletAvatars {
        val generatedImage = generateColoredAvatar(gradientSeed, name)
        return createAvatars(generatedImage)
    }
}

sealed class CircletAvatars {
    abstract val circle: Icon
    abstract val offline: Icon
    abstract val online: Icon

    object MainIcon : CircletAvatars() {
        override val circle: Icon = SpaceIcons.Main
        override val offline: Icon = SpaceIcons.Main
        override val online: Icon = SpaceIcons.Main
    }

    class Image(private val image: BufferedImage) : CircletAvatars() {
        private val cache: MutableMap<kotlin.Pair<Color, Int>, JBImageIcon> = mutableMapOf()

        override val circle: Icon by lazy { JBImageIcon(CircletAvatarUtils.createCircleImage(image)) }
        override val offline: Icon
            get() = createStatusIcon(Color(224, 85, 85))
        override val online: Icon
            get() = createStatusIcon(Color(98, 181, 67))

        private fun createStatusIcon(color: Color): JBImageIcon {
            val size = JBUI.scale(16)
            return cache.getOrPut(color to size) {
                val hiDpi = ImageUtil.ensureHiDPI(CircletAvatarUtils.buildImageWithStatus(image, color), ScaleContext.create())
                JBImageIcon(ImageUtil.scaleImage(hiDpi, size, size))
            }
        }
    }
}


