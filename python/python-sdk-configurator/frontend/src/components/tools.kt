package com.intellij.python.sdkConfigurator.frontend.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp

@Composable
internal fun measureText(text: String, textStyle: TextStyle): Dp {
  val textMeasurer = rememberTextMeasurer()
  return with(LocalDensity.current) {
    textMeasurer.measure(text, textStyle).size.width.toDp()
  }
}