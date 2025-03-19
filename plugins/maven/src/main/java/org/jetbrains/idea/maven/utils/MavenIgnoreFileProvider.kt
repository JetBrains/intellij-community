// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.project.stateStore
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.utils.MavenSerializedRepositoryManager.Companion.MAVEN_REPOSITORY_MANAGER_STORAGE

class MavenIgnoreFileProvider : IgnoredFileProvider {
  override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean {
    return filePath.name == MAVEN_REPOSITORY_MANAGER_STORAGE && filePath.parentPath?.path == project.basePath
  }

  override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> {
    return setOf(IgnoredBeanFactory.ignoreFile(FileUtil.toSystemIndependentName(project.stateStore.projectFilePath.parent.resolve(MAVEN_REPOSITORY_MANAGER_STORAGE).toString()), project))
  }

  override fun getIgnoredGroupDescription(): @NlsContexts.DetailedDescription String {
    return MavenProjectBundle.message("maven.ignored.projects.settings")
  }
}