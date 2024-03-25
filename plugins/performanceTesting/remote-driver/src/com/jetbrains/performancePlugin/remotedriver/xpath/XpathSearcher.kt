// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.performancePlugin.remotedriver.xpath

import com.intellij.driver.model.LocalRefDelegate
import com.intellij.driver.model.RefDelegate
import com.intellij.driver.model.RemoteRefDelegate
import com.intellij.driver.model.transport.Ref
import com.jetbrains.performancePlugin.remotedriver.dataextractor.TextToKeyCache
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.awt.Component
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

internal class XpathSearcher(textToKeyCache: TextToKeyCache) {
  val modelCreator = XpathDataModelCreator(textToKeyCache)
  private val xPath = XPathFactory.newInstance().newXPath()

  fun findComponent(xpathExpression: String, component: Component?): RefDelegate<Component> {
    val components = findComponents(xpathExpression, component)
    if (components.size > 1) {
      throw IllegalStateException("To many components found by xpath '$xpathExpression'")
    }
    else if (components.isEmpty()) {
      throw IllegalStateException("No components found by xpath '$xpathExpression'")
    }
    return components.first()
  }

  fun findComponents(xpathExpression: String, component: Component?): List<RefDelegate<Component>> {
    val model = modelCreator.create(component)
    val result = xPath.compile(xpathExpression).evaluate(model, XPathConstants.NODESET) as NodeList
    return (0 until result.length).mapNotNull { result.item(it) }.filterIsInstance<Element>().mapNotNull {
      if (it.hasAttribute("remoteId")) {
        val remoteId = it.getAttribute("remoteId")
        val className = it.getAttribute("javaclass")
        val identityHash = it.getAttribute("hashCode")
        RemoteRefDelegate(Ref(remoteId, className, identityHash.toInt(), null))
      }
      else {
        LocalRefDelegate(it.getUserData("component") as Component)
      }
    }
  }
}