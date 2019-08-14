package icons

import com.intellij.openapi.util.*

interface CircletIcons {
    companion object {
        @JvmField
        val mainIcon = IconLoader.getIcon("/icons/main.svg", CircletIcons::class.java)
    }
}
