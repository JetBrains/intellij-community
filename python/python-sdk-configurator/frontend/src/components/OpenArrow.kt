package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.theme.comboBoxStyle

/**
 * Arrow to open/close block
 */
@Composable
internal fun OpenArrow(opened: Boolean, onOpenChangeState: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  var opened by remember { mutableStateOf(opened) }
  val rotate = if (opened) 360f else 270f

  Icon(key = JewelTheme.comboBoxStyle.icons.chevronDown,
       "",
       Modifier.rotate(rotate).clickable(onClick = {
         opened = !opened
         onOpenChangeState(opened)
       }).then(modifier)
  )
}