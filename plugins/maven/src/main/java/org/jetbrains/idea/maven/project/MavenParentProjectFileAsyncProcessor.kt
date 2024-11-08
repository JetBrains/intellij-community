// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenArtifactUtil
import org.jetbrains.idea.maven.utils.MavenUtil

abstract class MavenParentProjectFileAsyncProcessor<RESULT_TYPE>(private val myProject: Project) {
  suspend fun process(generalSettings: MavenGeneralSettings,
              projectFile: VirtualFile,
              parentDesc: MavenParentDesc?): RESULT_TYPE? {
    var parentDesc = parentDesc
    val superPom = MavenUtil.resolveSuperPomFile(myProject, projectFile)
    if (superPom == null || projectFile == superPom) return null

    var result: RESULT_TYPE? = null

    if (parentDesc == null) {
      return processSuperParent(superPom)
    }

    var parentFile = findManagedFile(parentDesc.parentId)
    if (parentFile != null) {
      result = processManagedParent(parentFile)
    }

    if (result == null && Strings.isEmpty(parentDesc.parentRelativePath)) {
      result = findInLocalRepository(generalSettings, parentDesc)
      if (result == null) {
        parentDesc = MavenParentDesc(parentDesc.parentId, MavenDomProjectProcessorUtils.DEFAULT_RELATIVE_PATH)
      }
    }

    if (result == null && projectFile.parent != null) {
      parentFile = projectFile.parent.findFileByRelativePath(parentDesc.parentRelativePath)
      if (parentFile != null && parentFile.isDirectory) {
        parentFile = parentFile.findFileByRelativePath(MavenConstants.POM_XML)
      }
      if (parentFile != null) {
        result = processRelativeParent(parentFile)
      }
    }

    if (result == null) {
      result = findInLocalRepository(generalSettings, parentDesc)
    }

    return result
  }

  private suspend fun findInLocalRepository(generalSettings: MavenGeneralSettings, parentDesc: MavenParentDesc): RESULT_TYPE? {
    var result: RESULT_TYPE? = null
    val parentFile: VirtualFile?
    val parentIoFile = MavenArtifactUtil.getArtifactFile(generalSettings.effectiveRepositoryPath, parentDesc.parentId, "pom")
    parentFile = LocalFileSystem.getInstance().findFileByNioFile(parentIoFile)
    if (parentFile != null) {
      result = processRepositoryParent(parentFile)
    }
    return result
  }

  protected abstract fun findManagedFile(id: MavenId): VirtualFile?

  protected open suspend fun processManagedParent(parentFile: VirtualFile): RESULT_TYPE? {
    return doProcessParent(parentFile)
  }

  protected open suspend fun processRelativeParent(parentFile: VirtualFile): RESULT_TYPE? {
    return doProcessParent(parentFile)
  }

  protected open suspend fun processRepositoryParent(parentFile: VirtualFile): RESULT_TYPE? {
    return doProcessParent(parentFile)
  }

  protected open suspend fun processSuperParent(parentFile: VirtualFile): RESULT_TYPE? {
    return doProcessParent(parentFile)
  }

  protected abstract suspend fun doProcessParent(parentFile: VirtualFile): RESULT_TYPE?
}