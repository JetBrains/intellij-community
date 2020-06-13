package circlet.actions

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.SpaceIcons

object CircletActionUtils {
    fun showIconInActionSearch(e: AnActionEvent) {
        e.presentation.icon = if (e.place == ActionPlaces.ACTION_SEARCH) SpaceIcons.Main else null
    }
}
