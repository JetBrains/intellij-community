/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository.test

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import com.intellij.util.exists
import com.intellij.util.isFile
import gnu.trove.THashSet
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.Constants
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.net.URLEncoder
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.properties.Delegates

class InMemoryFsRule : ExternalResource() {
  private var _fs: FileSystem? = null

  private var sanitizedName: String by Delegates.notNull()

  override fun apply(base: Statement, description: Description): Statement {
    sanitizedName = URLEncoder.encode(description.methodName, Charsets.UTF_8.name())
    return super.apply(base, description)
  }

  val fs: FileSystem
    get() {
      var r = _fs
      if (r == null) {
        r = MemoryFileSystemBuilder
          .newLinux()
          .setCurrentWorkingDirectory("/")
          .build(sanitizedName)
        _fs = r
      }
      return r!!
    }

  override fun after() {
    _fs?.close()
    _fs = null
  }
}

private fun getChildrenStream(path: Path, excludes: Array<out String>? = null) = Files.list(path)
  .filter { !it.endsWith(Constants.DOT_GIT) && (excludes == null || !excludes.contains(it.fileName.toString())) }
  .sorted()

fun compareFiles(path1: Path, path2: Path, vararg localExcludes: String) {
  assertThat(path1).isDirectory()
  assertThat(path2).isDirectory()

  val notFound = THashSet<Path>()
  for (path in getChildrenStream(path1, localExcludes)) {
    notFound.add(path)
  }

  for (child2 in getChildrenStream(path2)) {
    val childName = child2.fileName.toString()
    val child1 = path1.resolve(childName)
    if (child1.isFile()) {
      assertThat(child2).hasSameContentAs(child1)
    }
    else if (!child1.exists()) {
      throw AssertionError("Path '$path2' must not contain '$childName'")
    }
    else {
      compareFiles(child1, child2, *localExcludes)
    }
    notFound.remove(child1)
  }

  if (notFound.isNotEmpty()) {
    throw AssertionError("Path '$path2' must contain ${notFound.joinToString { "'${it.toString().substring(1)}'"  }}.")
  }
}