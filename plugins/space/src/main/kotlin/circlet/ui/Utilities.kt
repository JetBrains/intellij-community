package circlet.ui

import com.intellij.openapi.wm.*
import java.awt.*

fun requestFocus(component: Component?) {
    if (component != null) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
            IdeFocusManager.getGlobalInstance().requestFocus(component, true)
        }
    }
}
