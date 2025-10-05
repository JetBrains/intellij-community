// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.html

import com.intellij.javaee.ExternalResourceManager
import com.intellij.polySymbols.context.PolyContextChangeListener

class HtmlPolyContextChangeListener: PolyContextChangeListener {
  override fun contextMayHaveChanged() {
    // Increase modification count to force reload of Xml attribute and tag descriptors
    ExternalResourceManager.getInstance().incModificationCount()
  }
}