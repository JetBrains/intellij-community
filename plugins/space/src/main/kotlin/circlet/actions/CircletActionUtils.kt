package circlet.actions

import com.intellij.openapi.actionSystem.*
import icons.*

object CircletActionUtils {
    fun showIconInActionSearch(e: AnActionEvent) {
        e.presentation.icon = if (e.place == ActionPlaces.ACTION_SEARCH) CircletIcons.mainIcon else null
    }
}
