// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository.test

import com.intellij.configurationStore.TestScheme
import com.intellij.configurationStore.TestSchemesProcessor
import com.intellij.configurationStore.save
import com.intellij.configurationStore.schemeManager.SchemeManagerImpl
import com.intellij.configurationStore.serialize
import com.intellij.testFramework.ProjectRule
import com.intellij.util.toByteArray
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.ReadonlySource
import org.jetbrains.settingsRepository.SyncType
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.junit.ClassRule
import org.junit.Test

private const val dirName = "keymaps"

class LoadTest : IcsTestCase() {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Suppress("UNCHECKED_CAST")
  private fun createSchemeManager(dirPath: String): SchemeManagerImpl<TestScheme, TestScheme> {
    return icsManager.schemeManagerFactory.value.create(dirPath, TestSchemesProcessor(), streamProvider = provider) as SchemeManagerImpl<TestScheme, TestScheme>
  }

  @Test fun `load scheme`() {
    val localScheme = TestScheme("local")
    provider.write("$dirName/local.xml", localScheme.serialize()!!.toByteArray())

    val schemeManager = createSchemeManager(dirName)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).containsOnly(localScheme)

    schemeManager.save()

    val dirPath = (icsManager.repositoryManager as GitRepositoryManager).repository.workTree.toPath().resolve(dirName)
    assertThat(dirPath).isDirectory()

    schemeManager.removeScheme(localScheme)
    schemeManager.save()

    assertThat(dirPath).doesNotExist()

    provider.write("$dirName/local1.xml", TestScheme("local1").serialize()!!.toByteArray())
    provider.write("$dirName/local2.xml", TestScheme("local2").serialize()!!.toByteArray())

    assertThat(dirPath.resolve("local1.xml")).isRegularFile()
    assertThat(dirPath.resolve("local2.xml")).isRegularFile()

    schemeManager.loadSchemes()
    schemeManager.removeScheme("local1")
    schemeManager.save()

    assertThat(dirPath.resolve("local1.xml")).doesNotExist()
    assertThat(dirPath.resolve("local2.xml")).isRegularFile()
  }

  @Test fun `load scheme with the same names`() {
    val localScheme = TestScheme("local")
    val data = localScheme.serialize()!!.toByteArray()
    provider.write("$dirName/local.xml", data)
    provider.write("$dirName/local2.xml", data)

    val schemeManager = createSchemeManager(dirName)
    schemeManager.loadSchemes()
    assertThat(schemeManager.allSchemes).containsOnly(localScheme)
  }

  @Test
  fun `load scheme from repo and read-only repo`() = runBlocking {
    val localScheme = TestScheme("local")

    provider.write("$dirName/local.xml", localScheme.serialize()!!.toByteArray())

    val remoteScheme = TestScheme("remote")
    val remoteRepository = tempDirManager.createRepository()
    remoteRepository
      .add("$dirName/Mac OS X from RubyMine.xml", remoteScheme.serialize()!!.toByteArray())
      .commit("add")

    remoteRepository.useAsReadOnlySource {
      val schemeManager = createSchemeManager(dirName)
      schemeManager.loadSchemes()
      assertThat(schemeManager.allSchemes).containsOnly(remoteScheme, localScheme)
      assertThat(schemeManager.isMetadataEditable(localScheme)).isTrue()
      assertThat(schemeManager.isMetadataEditable(remoteScheme)).isFalse()

      remoteRepository
        .delete("$dirName/Mac OS X from RubyMine.xml")
        .commit("delete")

      icsManager.sync(SyncType.MERGE)
      assertThat(schemeManager.allSchemes).containsOnly(localScheme)
    }
  }

  @Test fun `scheme overrides read-only`() {
    val schemeName = "Emacs"
    val localScheme = TestScheme(schemeName, "local")

    provider.write("$dirName/$schemeName.xml", localScheme.serialize()!!.toByteArray())

    val remoteScheme = TestScheme(schemeName, "remote")
    val remoteRepository = tempDirManager.createRepository("remote")
    remoteRepository
      .add("$dirName/$schemeName.xml", remoteScheme.serialize()!!.toByteArray())
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemeManager = createSchemeManager(dirName)
      schemeManager.loadSchemes()
      assertThat(schemeManager.allSchemes).containsOnly(localScheme)
      assertThat(schemeManager.isMetadataEditable(localScheme)).isFalse()
    }
  }

  private inline fun Repository.useAsReadOnlySource(runnable: () -> Unit) {
    createAndRegisterReadOnlySource()
    try {
      runnable()
    }
    finally {
      icsManager.readOnlySourcesManager.setSources(emptyList())
    }
  }

  fun Repository.createAndRegisterReadOnlySource(): ReadonlySource {
    val source = ReadonlySource(workTree.absolutePath)
    assertThat(cloneBare(source.url!!, icsManager.readOnlySourcesManager.rootDir.resolve(source.path!!)).objectDatabase.exists()).isTrue()
    icsManager.readOnlySourcesManager.setSources(listOf(source))
    return source
  }
}