// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee.impl

import com.intellij.javaee.UrlByNamespaceProvider
import com.intellij.openapi.project.Project
import com.intellij.util.containers.MultiMap

internal object DummyUrlByNamespaceProvider : UrlByNamespaceProvider {
  override fun getUrlsByNamespace(project: Project): MultiMap<String, String> {
    return MultiMap.empty()
  }
}