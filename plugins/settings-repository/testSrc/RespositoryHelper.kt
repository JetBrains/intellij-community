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

import com.intellij.mock.MockVirtualFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.isFile
import gnu.trove.THashSet
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.Constants
import java.nio.file.Files
import java.nio.file.Path

data class FileInfo(val name: String, val data: ByteArray)

fun fs() = MockVirtualFileSystem()

private fun getChildrenStream(path: Path, excludes: Array<out String>? = null) = Files.list(path)
  .filter { !it.endsWith(Constants.DOT_GIT) && (excludes == null || !excludes.contains(it.getFileName().toString())) }
  .sorted()

private fun compareFiles(path1: Path, path2: Path, path3: VirtualFile? = null, vararg localExcludes: String) {
  assertThat(path1).isDirectory()
  assertThat(path2).isDirectory()
  if (path3 != null) {
    assertThat(path3.isDirectory()).isTrue()
  }

  val notFound = THashSet<Path>()
  for (path in getChildrenStream(path1, localExcludes)) {
    notFound.add(path)
  }

  for (child2 in getChildrenStream(path2)) {
    val fileName = child2.getFileName()
    val child1 = path1.resolve(fileName)
    val child3 = path3?.findChild(fileName.toString())
    if (child1.isFile()) {
      assertThat(child2).hasSameContentAs(child1)
      if (child3 != null) {
        assertThat(child3.isDirectory()).isFalse()
        assertThat(child1).hasContent(if (child3 is LightVirtualFile) child3.getContent().toString() else VfsUtilCore.loadText(child3))
      }
    }
    else {
      compareFiles(child1, child2, child3, *localExcludes)
    }
    notFound.remove(child1)
  }

  assertThat(notFound).isEmpty()
}