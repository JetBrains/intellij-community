package circlet.ui

import java.awt.*
import java.awt.geom.*
import java.awt.image.*
import kotlin.math.*

object ImageUtils {
    fun createCircleImage(image: BufferedImage): BufferedImage {
        val size: Int = min(image.width, image.height)
        val avatarOvalArea = Area(Ellipse2D.Double(0.0, 0.0, size.toDouble(), size.toDouble()))

        return createImageByMask(image, avatarOvalArea)
    }

    @Suppress("unused")
    fun createRoundedImage(image: BufferedImage, arc: Double): BufferedImage {
        val size: Int = min(image.width, image.height)
        val avatarOvalArea = Area(RoundRectangle2D.Double(0.0, 0.0, size.toDouble(), size.toDouble(), arc, arc))
        return createImageByMask(image, avatarOvalArea)
    }

    fun createImageByMask(image: BufferedImage, area: Area): BufferedImage {
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

    fun applyQualityRenderingHints(g2: Graphics2D) {
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
    }
}
