// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.workspaceModel

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ExcludeUrlEntity
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance

object LegacyToWorkspaceImportUtil {
  // keep certain legacy import entities in workspace import
  fun retainLegacyImportEntities(project: Project, legacyStorage: MutableEntityStorage, newStorage: MutableEntityStorage) {
    retainPreviouslyExcludedFolders(project, legacyStorage, newStorage)
  }

  // if a folder was excluded in legacy import, keep it excluded in workspace import as well
  private fun retainPreviouslyExcludedFolders(project: Project, legacyStorage: MutableEntityStorage, newStorage: MutableEntityStorage) {
    val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
    val previouslyExcludedUrls = legacyStorage.entities(ExcludeUrlEntity::class.java)
    val newExcludedUrlMap = newStorage.entities(ExcludeUrlEntity::class.java).associateBy({ it.url }, { it })
    val newContentRootMap = newStorage.entities(ContentRootEntity::class.java).associateBy({ it.url }, { it })
    for (previouslyExcludedUrl in previouslyExcludedUrls) {
      val url = previouslyExcludedUrl.url
      if (!newExcludedUrlMap.containsKey(url)) {
        var parentUrl: VirtualFileUrl? = url
        while (parentUrl != null) {
          val newContentRoot = newContentRootMap[parentUrl]
          if (newContentRoot != null) {
            if (!newContentRoot.excludedUrls.map { it.url }.contains(url)) {
              newStorage.modifyEntity(newContentRoot) {
                val excludedUrls = this.excludedUrls.toMutableList()
                excludedUrls.add(ExcludeUrlEntity(url, previouslyExcludedUrl.entitySource))
                this.excludedUrls = excludedUrls
              }
            }
            break
          }
          parentUrl = virtualFileUrlManager.getParentVirtualUrl(parentUrl)
        }
      }
    }
  }
}