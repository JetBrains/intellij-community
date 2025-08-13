package com.intellij.python.sdk.ui.evolution.ui.components

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.ui.icons.scaleIconOrLoadCustomVersion
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.LafIconLookup.getDisabledIcon
import com.intellij.util.ui.LafIconLookup.getIcon
import com.intellij.util.ui.LafIconLookup.getSelectedIcon
import javax.swing.Icon
import kotlin.math.min
import kotlin.text.startsWith

data class IconViewport(val width: Int, val height: Int) {
  companion object {
    val UNLIMITED = IconViewport(-1, -1)
  }
}

data class PresentationIcons(val icon: Icon?, val selectedIcon: Icon?)

//fun Presentation.icons(action: AnAction, iconViewport: IconViewport = IconViewport.UNLIMITED): PresentationIcons {
//
//}

fun calcRawIcons(action: AnAction, presentation: Presentation, forceChecked: Boolean): Pair<Icon?, Icon?> {
  val hideIcon = presentation.getClientProperty(MenuItemPresentationFactory.HIDE_ICON) == true
  var icon = if (hideIcon) null else presentation.getIcon()
  var selectedIcon = if (hideIcon) null else presentation.selectedIcon
  var disabledIcon = if (hideIcon) null else presentation.disabledIcon

  if (icon == null && selectedIcon == null) {
    val actionId = ActionManager.getInstance().getId(action)
    if (actionId != null && actionId.startsWith("QuickList.")) {
      //icon =  null; // AllIcons.Actions.QuickList;
    }
    else if (action is Toggleable && (Toggleable.isSelected(presentation) || forceChecked)) {
      icon = getIcon("checkmark")
      selectedIcon = getSelectedIcon("checkmark")
      disabledIcon = getDisabledIcon("checkmark")
    }
  }
  if (!presentation.isEnabled) {
    icon = if (disabledIcon != null || icon == null) disabledIcon else getDisabledIcon(icon)
    selectedIcon = if (disabledIcon != null || selectedIcon == null) disabledIcon else getDisabledIcon(selectedIcon)
  }
  return icon to selectedIcon
}

fun scaleIconToSize(icon: Icon?, iconViewport: IconViewport): Icon? = when (icon) {
  null -> null
  is EmptyIcon -> {
    if (icon.iconWidth == iconViewport.width && icon.iconHeight == iconViewport.height) icon else EmptyIcon.create(iconViewport.width, iconViewport.height)
  }
  else -> {
    val scale = min(iconViewport.width, iconViewport.height).toFloat() / min(icon.iconWidth, icon.iconHeight)
    if (scale == 1f) icon else scaleIconOrLoadCustomVersion(icon, scale)
  }
}
