// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.google.common.io.Resources
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.jcef.JCEFHtmlPanel
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class PyPackagingJcefHtmlPanel(project: Project) : JCEFHtmlPanel(uniqueUrl) {
  private val loadedStyles: MutableMap<String, String> = mutableMapOf()
  private var myLastHtml: @NlsSafe String? = null

  private val cssStyleCodeToInject: String
    get() {
      val styleKey = when (val laf = LafManager.getInstance().getCurrentUIThemeLookAndFeel()) {
        is UIThemeLookAndFeelInfo -> when (laf.id) {
          "ExperimentalDark" -> "python_packaging_toolwindow_dark.css"
          "ExperimentalLight" -> "python_packaging_toolwindow_light.css"
          "JetBrainsHighContrastTheme" -> "python_packaging_toolwindow_high_contrast.css"
          else -> "python_packaging_toolwindow_default.css"
        }
        else -> "python_packaging_toolwindow_default.css"
      }
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
    project.messageBus.connect(this).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (myLastHtml != null) setHtml(myLastHtml!!)
    })
    setOpenLinksInExternalBrowser(true)
  }

  override fun prepareHtml(html: String): String = html.replaceFirst("<head>", "<head>$cssStyleCodeToInject")

  override fun setHtml(html: String) {
    myLastHtml = html
    super.setHtml(html)
  }

  companion object {
    private val ourCounter = AtomicInteger(-1)
    private val ourClassUrl = PyPackagingJcefHtmlPanel::class.java.getResource(
      PyPackagingJcefHtmlPanel::class.java.simpleName + ".class")!!.toExternalForm()
    private val uniqueUrl: String
      get() = ourClassUrl + "@" + ourCounter.incrementAndGet()
  }
}
