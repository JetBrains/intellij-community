// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.utils

import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxyCredentialStore
import com.intellij.util.net.ProxySettings

internal object PyProxyUtils {
  val proxyString: String?
    get() {
      val configuration = ProxySettings.getInstance().getProxyConfiguration()
      if (configuration is ProxyConfiguration.StaticProxyConfiguration) {
        val credentials = ProxyCredentialStore.getInstance().getCredentials(configuration.host, configuration.port)
        val credentialStr = if (credentials?.userName != null) "${credentials.userName}}:${credentials.password}@" else ""
        return "http://${credentialStr}${configuration.host}:${configuration.port}"
      }
      return null
    }
}
