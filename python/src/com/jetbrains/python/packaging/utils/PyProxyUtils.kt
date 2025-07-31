// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.utils

import com.intellij.util.net.HttpConfigurable

internal object PyProxyUtils {
  val proxyString: String?
    get() {
      val settings = HttpConfigurable.getInstance()
      if (settings != null && settings.USE_HTTP_PROXY) {
        val credentials = if (settings.PROXY_AUTHENTICATION) "${settings.proxyLogin}:${settings.plainProxyPassword}@" else ""
        return "http://$credentials${settings.PROXY_HOST}:${settings.PROXY_PORT}"
      }
      return null
    }
}