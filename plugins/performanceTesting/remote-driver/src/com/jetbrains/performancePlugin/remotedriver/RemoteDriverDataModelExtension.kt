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
    // The driver Invoker registers only under -Dcom.sun.management.jmxremote. The UI automation MCP
    // snapshot runs without that flag and does not read these driver references, so skip them when absent.
    val invokerService = InvokerService.getInstance()
    if (!invokerService.isReady()) return
    val ref = invokerService.putReference(component)
    element.setAttribute("refId", ref.id)
    element.setAttribute("hashCode", ref.identityHashCode.toString())
    element.setAttribute("asString", ref.asString())
    element.setAttribute("rdTarget", invokerService.rdTarget.toString())
  }
}