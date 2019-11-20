package circlet.ui

import com.intellij.util.ui.*
import java.awt.*
import java.awt.geom.*
import java.awt.image.*

open class ClippedImageIcon(private val image: BufferedImage,
                            private val clip: (img: BufferedImage) -> Shape) : JBImageIcon(image) {
    override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
        g?.clip = clip(image)
        super.paintIcon(c, g, x, y)
    }
}

class CircleImageIcon(image: BufferedImage) : ClippedImageIcon(image, { img ->
    val size = img.width.toDouble()
    Ellipse2D.Double(0.0, 0.0, size, size)
})
