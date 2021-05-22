// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.toolwindow

import com.google.common.io.Resources
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.JCEFHtmlPanel
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger


class PyPackagingJcefHtmlPanel(project: Project) : JCEFHtmlPanel(uniqueUrl) {
  private val openInBrowser = JBCefJSQuery.create(this as JBCefBrowserBase)
  private val loadedStyles: MutableMap<String, String> = mutableMapOf()
  private val myCefLoadHandler: CefLoadHandler
  private var myLastHtml: @NlsSafe String? = null
  private val jsCodeToInject: String

  private val cssStyleCodeToInject: String
    get() {
      val styleKey = if (UIUtil.isUnderDarcula()) "python_packaging_toolwindow_darcula.css" else "python_packaging_toolwindow_default.css"
      return loadedStyles.getOrPut(styleKey) {
        try {
          val resource = PyPackagingJcefHtmlPanel::class.java.getResource("/styles/$styleKey") ?: error("Failed to load $styleKey")
          val loadedCss = Resources.toString(resource, StandardCharsets.UTF_8)
          "<style>$loadedCss</style>"
        }
        catch (e: IOException) {
          throw RuntimeException("Failed to load $styleKey", e)
        }
      }
    }


  init {
    try {
      val script = PyPackagingJcefHtmlPanel::class.java.getResource("/js/pkg_toolwindow_open_in_browser.js")
                   ?: error("Failed to load js script for Python Packaging toolwindow.")
      jsCodeToInject = Resources.toString(script, StandardCharsets.UTF_8)
    }
    catch (e: IOException) {
      throw RuntimeException("Failed to load js script for Python Packaging toolwindow.", e)
    }

    myCefLoadHandler = object : CefLoadHandlerAdapter() {
      override fun onLoadingStateChange(browser: CefBrowser, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        if (jsCodeToInject != null) {
          browser.executeJavaScript(jsCodeToInject, cefBrowser.url, 0)
          browser.executeJavaScript("window.__IntelliJTools.openInBrowserCallback = link => {"
                                    + openInBrowser.inject("link") + "}",
                                    cefBrowser.url, 0)
        }
      }
    }
    jbCefClient.addLoadHandler(myCefLoadHandler, cefBrowser)

    openInBrowser.addHandler {
      if (it.isNotEmpty()) BrowserUtil.browse(it)
      null
    }
    Disposer.register(this, openInBrowser)

    project.messageBus.connect(this).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (myLastHtml != null) setHtml(myLastHtml!!)
    })
  }

  override fun prepareHtml(html: String): String = html.replaceFirst("<head>", "<head>$cssStyleCodeToInject")

  override fun setHtml(html: String) {
    myLastHtml = html
    super.setHtml(html)
  }

  override fun dispose() {
    super.dispose()
    jbCefClient.removeLoadHandler(myCefLoadHandler, cefBrowser)
  }

  companion object {
    private val ourCounter = AtomicInteger(-1)
    private val ourClassUrl = PyPackagingJcefHtmlPanel::class.java.getResource(
      PyPackagingJcefHtmlPanel::class.java.simpleName + ".class")!!.toExternalForm()
    private val uniqueUrl: String
      get() = ourClassUrl + "@" + ourCounter.incrementAndGet()
  }
}