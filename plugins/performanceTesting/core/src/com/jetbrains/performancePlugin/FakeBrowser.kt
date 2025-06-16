// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class FakeBrowser: BrowserLauncher() {
  companion object {
    var latestUrl: String? = null
  }

  fun getLatestUrl(): String? {
    return latestUrl
  }

  override fun open(url: String) {
    latestUrl = url
  }

  override fun browse(file: File) {
    latestUrl = file.absolutePath
  }

  override fun browse(file: Path) {
    latestUrl = file.absolutePathString()
  }

  override fun browse(url: String, browser: WebBrowser?, project: Project?) {
    latestUrl = url
  }
}