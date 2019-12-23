package icons

import com.intellij.openapi.util.*

object CircletIcons {
    @JvmField
    val mainIcon = IconLoader.getIcon("/icons/main.svg", CircletIcons::class.java)
    @JvmField
    val mainToolWindowIcon = IconLoader.getIcon("/icons/main_toolwindow.svg", CircletIcons::class.java) // 13x13

    @JvmField
    val user = IconLoader.getIcon("/icons/user.svg", CircletIcons::class.java)
    @JvmField
    val userOnline = IconLoader.getIcon("/icons/userOnline.svg", CircletIcons::class.java)
    @JvmField
    val userOffline = IconLoader.getIcon("/icons/userOffline.svg", CircletIcons::class.java)
}
