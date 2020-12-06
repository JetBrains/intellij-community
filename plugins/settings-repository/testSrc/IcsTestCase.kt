// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.testFramework.DisposableRule
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
import org.junit.rules.RuleChain
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

const val SAMPLE_FILE_NAME = "file.xml"
const val SAMPLE_FILE_CONTENT = """<application>
  <component name="Encoding" default_encoding="UTF-8" />
</application>"""

abstract class IcsTestCase {
  val tempDirManager = TemporaryDirectory()
  fun getTemporaryFolder() = tempDirManager

  private val fsRule: InMemoryFsRule = InMemoryFsRule()
  private val disposableRule: DisposableRule = DisposableRule()

  @JvmField
  @Rule
  val ruleChain: RuleChain = RuleChain.outerRule(fsRule).around(tempDirManager).around(disposableRule)

  val fs: FileSystem
    get() = fsRule.fs

  val icsManager by lazy(LazyThreadSafetyMode.NONE) {
    val path = tempDirManager.newPath().resolve("settingsRepository")
    val icsManager = IcsManager(path, disposableRule.disposable,
                                lazy { SchemeManagerFactoryBase.TestSchemeManagerFactory(path.resolve("repository")) })

    if (createAndActivateRepository()) {
      icsManager.repositoryManager.createRepositoryIfNeeded()
      icsManager.isRepositoryActive = true
    }
    icsManager
  }

  open fun createAndActivateRepository() : Boolean = true

  val provider by lazy(LazyThreadSafetyMode.NONE) { icsManager.ApplicationLevelProvider() }

}

fun TemporaryDirectory.createRepository(directoryName: String? = null) = createGitRepository(newPath(directoryName))

private fun createGitRepository(dir: Path): Repository {
  val repository = buildRepository(workTree = dir)
  repository.create()
  return repository
}
