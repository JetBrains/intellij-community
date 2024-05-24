package com.jetbrains.performancePlugin.remotedriver.dataextractor

import com.intellij.driver.model.TextData
import com.intellij.openapi.extensions.ExtensionPointName
import java.awt.Component

interface TextExtractorExtension {
  companion object {
    val EP_NAME = ExtensionPointName.create<TextExtractorExtension>("com.jetbrains.performancePlugin.remotedriver.textExtractorExtension")
  }

  fun extractTextFromComponent(component: Component): List<TextData>
}