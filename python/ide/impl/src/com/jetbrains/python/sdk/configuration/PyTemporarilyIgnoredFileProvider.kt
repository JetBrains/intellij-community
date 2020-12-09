// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.jetbrains.python.PyCharmCommunityCustomizationBundle
import org.jetbrains.annotations.SystemIndependent

internal class PyTemporarilyIgnoredFileProvider : IgnoredFileProvider {

  companion object {
    private val LOGGER = Logger.getInstance(PyTemporarilyIgnoredFileProvider::class.java)
    private val IGNORED_ROOTS = mutableSetOf<String>()

    internal fun ignoreRoot(path: @SystemIndependent String, parent: Disposable) {
      Disposer.register(
        parent,
        {
          IGNORED_ROOTS.remove(path)
          LOGGER.info("$path has been removed from ignored roots")
        }
      )

      IGNORED_ROOTS.add(path)
      LOGGER.info("$path has been added to ignored roots")
    }
  }

  override fun isIgnoredFile(project: Project, filePath: FilePath): Boolean {
    val path = filePath.path
    return IGNORED_ROOTS.any { FileUtil.isAncestor(it, path, false) }
  }

  override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> = emptySet()

  override fun getIgnoredGroupDescription(): String {
    return PyCharmCommunityCustomizationBundle.message("temporarily.ignored.file.provider.description")
  }
}