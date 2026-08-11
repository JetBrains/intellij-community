package com.intellij.python.sdk.frontend.evolution

import com.intellij.openapi.util.NlsSafe
import com.intellij.python.sdk.common.evolution.EvoSdkDto
import com.intellij.python.sdk.common.evolution.getAddress
import com.intellij.python.sdk.frontend.PySdkFrontendBundle

internal fun EvoSdkDto.getTitle(): String {
  val pythonVersion = this.pythonVersion ?: "?"
  val address = this.getAddress()
  return when {
    this.pythonBinaryPath != null -> "$address ʿ${pythonVersion}ʾ"
    else -> address
  }
}

internal fun EvoSdkDto.getCurrentTitle(): String {
  val pythonVersion = this.pythonVersion ?: "?"
  val address = this.getAddress()
  return "Python $pythonVersion" + (if (address.isNotBlank()) " ($address)" else "")
}

internal fun EvoSdkDto.getTitle(isSelected: Boolean): String {
  val title = getTitle()
  val marker = when {
    isSelected -> "⭐"
    else -> ""
  }
  return "$marker$title"
}

internal fun EvoSdkDto.getDescription(): @NlsSafe String {
  return pythonBinaryPath ?: PySdkFrontendBundle.message("evo.sdk.undefined.description")
}
