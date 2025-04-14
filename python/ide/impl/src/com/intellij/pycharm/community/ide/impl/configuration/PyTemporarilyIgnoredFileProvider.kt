// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import java.nio.file.Path

internal class PyTemporarilyIgnoredFileProvider : IgnoredFileProvider {

  companion object {
    private val LOGGER = Logger.getInstance(PyTemporarilyIgnoredFileProvider::class.java)
    private val IGNORED_ROOTS = mutableSetOf<Path>()

    internal fun ignoreRoot(path: Path, parent: Disposable) {
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
    val path = Path.of(filePath.path)
    return IGNORED_ROOTS.any { path.startsWith(it) }
  }

  override fun getIgnoredFiles(project: Project): Set<IgnoredFileDescriptor> = emptySet()

  override fun getIgnoredGroupDescription(): String {
    return PyCharmCommunityCustomizationBundle.message("temporarily.ignored.file.provider.description")
  }
}