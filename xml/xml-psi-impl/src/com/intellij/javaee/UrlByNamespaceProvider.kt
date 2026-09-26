// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.javaee.impl.DummyUrlByNamespaceProvider
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.util.containers.MultiMap

interface UrlByNamespaceProvider {
  fun getUrlsByNamespace(project: Project): MultiMap<String, String>

  companion object {
    @JvmStatic
    fun getInstance(): UrlByNamespaceProvider {
      return serviceOrNull<UrlByNamespaceProvider>() ?: DummyUrlByNamespaceProvider
    }
  }
}
