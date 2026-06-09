// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mermaid.markdown.preview

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefOSRHandlerFactory
import com.intellij.util.Function
import com.intellij.util.application
import java.awt.Rectangle
import javax.swing.JComponent

internal fun createClient(): JBCefClient {
  return JBCefApp.getInstance().createClient().apply {
    setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 20)
  }
}

fun createBrowser(): JBCefBrowser {
  val client = createClient()
  val builder = JBCefBrowser.createBuilder().apply {
    // Use fixed resolution in tests
    if (application.isUnitTestMode) {
      withScreenBounds(1920, 1080)
    }
    setOffScreenRendering(true)
    setCreateImmediately(true)
    setClient(client)
  }
  return builder.build()
}

internal fun JBCefBrowserBuilder.withScreenBounds(width: Int, height: Int): JBCefBrowserBuilder {
  return setOSRHandlerFactory(object: JBCefOSRHandlerFactory {
    override fun createScreenBoundsProvider(): Function<in JComponent, out Rectangle> {
      return Function { Rectangle(width, height) }
    }
  })
}
