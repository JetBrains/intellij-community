// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.remotedriver

import com.jetbrains.performancePlugin.jmxDriver.InvokerService
import com.jetbrains.performancePlugin.remotedriver.xpath.XpathDataModelExtension
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Component

class RemoteDriverDataModelExtension : XpathDataModelExtension {
  override val isRemDevExtension: Boolean = false

  override fun postProcessElement(doc: Document, component: Component, element: Element, parentElement: Element) {
    val component = element.getUserData("component") as? Component ?: return
    val ref = InvokerService.getInstance().putReference(component)
    element.setAttribute("refId", ref.id)
    element.setAttribute("hashCode", ref.identityHashCode.toString())
    element.setAttribute("asString", ref.asString())

    val rdTarget = InvokerService.getInstance().rdTarget
    element.setAttribute("rdTarget", rdTarget.toString())
  }
}