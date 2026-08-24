// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.CachedValueImpl
import com.intellij.util.indexing.IndexableSetContributor

internal class ExternalResourcesRootsProvider : IndexableSetContributor() {
  private val myStandardResources: CachedValue<MutableSet<VirtualFile>> = CachedValueImpl(CachedValueProvider {
    val manager = ExternalResourceManager.getInstance() as ExternalResourceManagerExImpl
    val duplicateCheck = HashSet<String>()
    val set = HashSet<VirtualFile>()
    for (map in manager.getStandardResources()) {
      for (resource in map.values) {
        var url = resource.getResourceUrl()
        if (url != null) {
          url = url.substring(0, url.lastIndexOf('/') + 1)
          if (duplicateCheck.add(url)) {
            val file = VfsUtilCore.findRelativeFile(url, null)
            if (file != null) {
              set.add(file)
            }
          }
        }
      }
    }
    CachedValueProvider.Result.create(set, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
  })

  override fun getAdditionalRootsToIndex(): Set<VirtualFile> {
    val standardResources = myStandardResources.getValue()
    val roots = HashSet<VirtualFile>(standardResources.size + 1)
    roots.addAll(standardResources)
    val path = FetchExtResourceAction.getExternalResourcesPath()
    val extResources = LocalFileSystem.getInstance().findFileByPath(path)
    if (extResources != null) {
      roots.add(extResources)
    }
    return roots
  }
}
