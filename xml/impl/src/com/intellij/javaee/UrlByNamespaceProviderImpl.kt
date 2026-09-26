// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.MultiMap
import com.intellij.xml.index.XmlNamespaceIndex

class UrlByNamespaceProviderImpl : UrlByNamespaceProvider {
  private val urlByNamespaceProvider = CachedValueProvider {
    val result = MultiMap<String, String>()

    val manager = ExternalResourceManager.getInstance() as ExternalResourceManagerExBase
    for (map in manager.getStandardResources()) {
      for (entry in map.entries) {
        val url = entry.value.getResourceUrl() ?: continue
        val file = VfsUtilCore.findRelativeFile(url, null) ?: continue
        val namespace = XmlNamespaceIndex.computeNamespace(file) ?: continue
        result.putValue(namespace, entry.key)
      }
    }
    CachedValueProvider.Result.create(result, manager)
  }

  override fun getUrlsByNamespace(project: Project): MultiMap<String, String> {
    return CachedValuesManager.getManager(project).getCachedValue(project, urlByNamespaceProvider)
  }
}
