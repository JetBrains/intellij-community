package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.SimpleListItem
import org.jetbrains.jewel.ui.icons.AllIconsKeys.Language.Python

@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun PythonsDropDown(pythons: ImmutableList<String>, grayedOut: Boolean = false, modifier: Modifier = Modifier) {
  val ts = JewelTheme.defaultTextStyle
  val longestPython = remember { pythons.maxBy { it.length } }
  val padding = 100.dp
  val width = measureText(longestPython, ts) + padding // Padding
  var i by remember { mutableIntStateOf(0) }
  ListComboBox(
    items = pythons,
    selectedIndex = i,
    modifier = modifier.widthIn(min = width, max = width + padding),
    onSelectedItemChange = { i = it },
    itemKeys = { index, _ -> index },
    itemContent = { item, isSelected, isActive ->
      SimpleListItem(
        text = item,
        selected = isSelected,
        active = isActive,
        icon = Python,
        colorFilter = if (grayedOut) ColorFilter.tint(Color.Gray) else null,
      )
    },
  )
}