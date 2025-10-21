package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.ui.component.ListComboBox

@Composable
internal fun PythonsDropDown(pythons: ImmutableList<String>, modifier: Modifier = Modifier) {
  var i by remember { mutableIntStateOf(0) }
  ListComboBox(pythons, i, { i = it }, modifier)
}