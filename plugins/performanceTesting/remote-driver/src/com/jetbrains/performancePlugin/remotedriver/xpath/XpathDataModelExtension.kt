// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver.xpath

import com.intellij.openapi.extensions.ExtensionPointName
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Component

interface XpathDataModelExtension {
  companion object {
    val EP_NAME = ExtensionPointName.create<XpathDataModelExtension>("com.jetbrains.performancePlugin.remotedriver.xpathDataModelExtension")
  }

  val isRemDevExtension: Boolean

  fun postProcessElement(doc: Document, component: Component, element: Element, parentElement: Element)
}