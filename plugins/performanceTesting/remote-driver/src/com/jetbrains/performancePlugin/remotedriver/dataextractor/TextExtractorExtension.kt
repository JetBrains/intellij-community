// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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