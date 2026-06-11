// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import java.nio.file.Path

internal sealed interface WebViewAssetSource {
  data class Classpath(
    val owner: Class<*>,
    val root: WebViewAssetPath,
    val devSourceRoot: Path?,
  ) : WebViewAssetSource

  data class Directory(
    val root: Path,
  ) : WebViewAssetSource
}
