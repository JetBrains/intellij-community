package com.jetbrains.packagesearch.intellij.plugin.ui.util

import com.intellij.ui.scale.JBUIScale

@ScaledPixels
internal fun Int.scaledAsString() = scaled().toString()

@ScaledPixels
internal fun Int.scaled() = JBUIScale.scale(this)

@ScaledPixels
internal fun Int.scaledFontSize() = JBUIScale.scale(this).toFloat()
