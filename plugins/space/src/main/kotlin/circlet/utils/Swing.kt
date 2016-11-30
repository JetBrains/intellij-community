package circlet.utils

import com.intellij.util.ui.*

val Int.px: Int
    get() = JBUI.scale(this)
