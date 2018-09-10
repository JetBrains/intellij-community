// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.writeChild
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.IcsManager
import org.jetbrains.settingsRepository.git.AddLoadedFile
import org.jetbrains.settingsRepository.git.DeleteFile
import org.jetbrains.settingsRepository.git.buildRepository
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

fun Repository.delete(path: String): Repository {
  edit(DeleteFile(path))
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
    val icsManager = IcsManager(tempDirManager.newPath(), lazy { SchemeManagerFactoryBase.TestSchemeManagerFactory(tempDirManager.newPath()) })
    icsManager.repositoryManager.createRepositoryIfNeed()
    icsManager.isRepositoryActive = true
    icsManager
  }

  val provider by lazy(LazyThreadSafetyMode.NONE) { icsManager.ApplicationLevelProvider() }
}

fun TemporaryDirectory.createRepository(directoryName: String? = null) = createGitRepository(newPath(directoryName))

private fun createGitRepository(dir: Path): Repository {
  val repository = buildRepository(workTree = dir)
  repository.create()
  return repository
}
