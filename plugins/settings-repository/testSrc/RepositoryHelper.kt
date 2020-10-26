// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.util.containers.CollectionFactory
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.isFile
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.Constants
import java.nio.file.Path

fun compareFiles(path1: Path, path2: Path, vararg localExcludes: String) {
  assertThat(path1).isDirectory()
  assertThat(path2).isDirectory()

  val notFound = CollectionFactory.createSmallMemoryFootprintSet<Path>()
  path1.directoryStreamIfExists({ !it.endsWith(Constants.DOT_GIT) && !localExcludes.contains(it.fileName.toString()) }) {
    notFound.addAll(it)
  }

  path2.directoryStreamIfExists({ !it.endsWith(Constants.DOT_GIT) }) {
    for (child2 in it) {
      val childName = child2.fileName.toString()
      val child1 = path1.resolve(childName)
      if (child1.isFile()) {
        assertThat(child2).hasSameTextualContentAs(child1)
      }
      else if (!child1.exists()) {
        throw AssertionError("Path '$path2' must not contain '$childName'")
      }
      else {
        compareFiles(child1, child2, *localExcludes)
      }
      notFound.remove(child1)
    }
  }

  if (notFound.isNotEmpty()) {
    throw AssertionError("Path '$path2' must contain ${notFound.joinToString { "'${it.toString().substring(1)}'"  }}.")
  }
}