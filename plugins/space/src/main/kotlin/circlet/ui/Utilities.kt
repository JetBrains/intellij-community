package circlet.ui

import com.intellij.openapi.wm.*
import com.intellij.util.*
import com.intellij.util.ui.*
import java.awt.*
import javax.swing.*

fun requestFocus(component: Component?) {
    if (component != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(component, true)
        }
    }
}

fun resizeIcon(icon: Icon, size: Int): Icon {
    val scale = JBUI.scale(size).toFloat() / icon.iconWidth.toFloat()
    return IconUtil.scale(icon, null, scale)
}

fun cleanupUrl(url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removeSuffix("/")
