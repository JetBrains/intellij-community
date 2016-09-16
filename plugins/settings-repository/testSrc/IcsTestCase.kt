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

import com.intellij.testFramework.InMemoryFsRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.io.writeChild
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.IcsManager
import org.jetbrains.settingsRepository.git.AddLoadedFile
import org.jetbrains.settingsRepository.git.createGitRepository
import org.jetbrains.settingsRepository.git.edit
import org.junit.Rule
import java.nio.file.FileSystem
import java.nio.file.Path

fun Repository.add(path: String, data: String) = add(path, data.toByteArray())

fun Repository.add(path: String, data: ByteArray): Repository {
  workTreePath.writeChild(path, data)
  edit(AddLoadedFile(path, data))
  return this
}

val Repository.workTreePath: Path
  get() = workTree.toPath()

val SAMPLE_FILE_NAME = "file.xml"
val SAMPLE_FILE_CONTENT = """<application>
  <component name="Encoding" default_encoding="UTF-8" />
</application>"""

abstract class IcsTestCase {
  val tempDirManager = TemporaryDirectory()
  @Rule fun getTemporaryFolder() = tempDirManager

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  val fs: FileSystem
    get() = fsRule.fs

  val icsManager by lazy(LazyThreadSafetyMode.NONE) {
    val icsManager = IcsManager(tempDirManager.newPath())
    icsManager.repositoryManager.createRepositoryIfNeed()
    icsManager.repositoryActive = true
    icsManager
  }

  val provider by lazy(LazyThreadSafetyMode.NONE) { icsManager.ApplicationLevelProvider() }
}

fun TemporaryDirectory.createRepository(directoryName: String? = null) = createGitRepository(newPath(directoryName))
