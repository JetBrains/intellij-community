// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.performancePlugin.remotedriver.xpath

import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import org.w3c.dom.NodeList
import java.awt.Component
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal class XpathSearcher(textToKeyCache: TextToKeyCache) {
  private val modelCreator = XpathDataModelCreator(textToKeyCache)
  private val xPath = XPathFactory.newInstance().newXPath()

  fun findComponent(xpathExpression: String, component: Component?): Component {
    val components = findComponents(xpathExpression, component)
    if (components.size > 1) {
      throw IllegalStateException("To many components found by xpath '$xpathExpression'")
    }
    else if (components.isEmpty()) {
      throw IllegalStateException("No components found by xpath '$xpathExpression'")
    }
    return components.first()
  }

  fun findComponents(xpathExpression: String, component: Component?): List<Component> {
    val model = modelCreator.create(component)
    val result = xPath.compile(xpathExpression).evaluate(model, XPathConstants.NODESET) as NodeList
    return (0 until result.length).mapNotNull { result.item(it).getUserData("component") as? Component }
  }
}