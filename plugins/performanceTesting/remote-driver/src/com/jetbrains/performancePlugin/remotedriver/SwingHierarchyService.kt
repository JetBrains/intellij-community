// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver

import com.intellij.driver.model.TextDataList
import com.intellij.openapi.components.Service
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextParser
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelCreator
import org.jsoup.helper.W3CDom
import java.awt.Component

@Suppress("unused")
@Service(Service.Level.APP)
class SwingHierarchyService {
  fun getSwingHierarchyAsDOM(component: Component?, onlyFrontend: Boolean): String {
    val creator = XpathDataModelCreator()
    if (onlyFrontend) {
      creator.elementProcessors.removeIf { it.isRemDevExtension }
    }

    val doc = creator.create(component)

    sanitizeXmlContent(doc.documentElement)

    return W3CDom().asString(doc)
  }

  fun findAllText(component: Component): TextDataList {
    return TextParser.parseComponent(component).let { TextDataList().apply { addAll(it) } }
  }
}