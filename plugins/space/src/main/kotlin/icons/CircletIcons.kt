package icons

import com.intellij.openapi.util.*

object CircletIcons {
    @JvmField
    val mainIcon = IconLoader.getIcon("/icons/main.svg", CircletIcons::class.java)
    @JvmField
    val mainToolWindowIcon = IconLoader.getIcon("/icons/main_toolwindow.svg", CircletIcons::class.java) // 13x13

    @JvmField
    val statusOnline = IconLoader.getIcon("/icons/online.svg", CircletIcons::class.java) // 6x6
    @JvmField
    val statusOffline = IconLoader.getIcon("/icons/offline.svg", CircletIcons::class.java) // 6x6
}
