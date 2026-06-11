// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
data class WebViewResourceDirectory(
  val anchorClass: Class<*>,
  val directoryPath: String,
)

@ApiStatus.Experimental
@Service(Service.Level.APP)
class WebViewAssetRootFactory {
  fun fromResourceDirectory(directory: WebViewResourceDirectory): WebViewAssetRoot {
    return WebViewAssetRoot.fromClasspath(directory.anchorClass, WebViewAssetPath.of(directory.directoryPath))
  }

  companion object {
    @JvmStatic
    fun getInstance(): WebViewAssetRootFactory = service()
  }
}
