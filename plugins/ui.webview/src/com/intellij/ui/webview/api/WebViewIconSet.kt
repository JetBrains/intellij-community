// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.webview.api

import org.jetbrains.annotations.ApiStatus

/**
 * Named WebView icon namespace backed by one fixed JVM classloader.
 *
 * The [id] is used by frontend `IconSet.define(id)`. Resource paths requested from that frontend object are resolved as
 * classpath resource names from the classloader of the owner class passed to [of].
 */
@ApiStatus.Experimental
class WebViewIconSet private constructor(
  val id: String,
  internal val classLoader: ClassLoader,
) {
  companion object {
    private val ID_PATTERN = Regex("[A-Za-z][A-Za-z0-9._-]*")

    @JvmStatic
    fun of(id: String, owner: Class<*>): WebViewIconSet {
      require(isValidId(id)) { "Invalid WebView icon set id: $id" }
      return WebViewIconSet(id, owner.classLoader ?: ClassLoader.getSystemClassLoader())
    }

    internal fun isValidId(id: String): Boolean = ID_PATTERN.matches(id)
  }
}
