@file:Suppress("UnstableApiUsage")

package com.intellij.python.sdk.ui.evolution.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.sdk.ui.PySdkUiBundle
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk

internal fun EvoSdk.getTitle(): String {
  val pythonVersion = this.pythonVersion?.toString() ?: "?"
  val address = this.getAddress()
  return when {
    this.pythonBinaryPath != null -> "$address ʿ${pythonVersion}ʾ"
    else -> address
  }
}

internal fun EvoSdk.getCurrentTitle(): String {
  val pythonVersion = this.pythonVersion?.toString() ?: "?"
  val address = this.getAddress()
  return "Python $pythonVersion" + (if (address.isNotBlank()) " ($address)" else "")
}


internal fun EvoSdk.getTitle(isSelected: Boolean): String {
  val title = getTitle()
  val marker = when {
    isSelected -> "⭐" //⭐✨🌟 🐍🦄🦩🚩🌝✓ʿʾ〘〙「」
    else -> ""
  }
  return "$marker$title"
}

internal fun EvoSdk.getDescription(): @NlsSafe String {
  val description = pythonBinaryPath?.toString()
                    ?: PySdkUiBundle.message("evo.sdk.undefined.description")

  return description
}