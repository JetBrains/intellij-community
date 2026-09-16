// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.project.isDirectoryBased
import com.intellij.project.isEqualToProjectFileStorePath
import com.intellij.project.stateStore
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.settings.MARKDOWN_SETTINGS_FILE_NAME
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

internal class MarkdownIgnoredFileProvider : IgnoredFileProvider {
  override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean =
    isEqualToProjectFileStorePath(project, Path.of(filePath.path), MARKDOWN_SETTINGS_FILE_NAME)

  override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> {
    if (!project.isDirectoryBased) {
      return emptySet()
    }
    val settingsFile = project.stateStore.storageManager.expandMacro(MARKDOWN_SETTINGS_FILE_NAME)
    return setOf(IgnoredBeanFactory.ignoreFile(settingsFile.invariantSeparatorsPathString, project))
  }

  override fun getIgnoredGroupDescription(): @NlsContexts.DetailedDescription String =
    MarkdownBundle.message("markdown.ignored.files.group.description")
}
