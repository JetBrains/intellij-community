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

import com.intellij.configurationStore.SchemeManagerImpl
import com.intellij.configurationStore.TestScheme
import com.intellij.configurationStore.TestSchemesProcessor
import com.intellij.testFramework.ProjectRule
import com.intellij.util.xmlb.serialize
import com.intellij.util.xmlb.toByteArray
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.lib.Repository
import org.jetbrains.settingsRepository.ReadonlySource
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.junit.ClassRule
import org.junit.Test

class LoadTest : IcsTestCase() {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()
  }

  private val dirPath = "\$ROOT_CONFIG$/keymaps"

  private fun createSchemeManager(dirPath: String) = SchemeManagerImpl<TestScheme, TestScheme>(dirPath, TestSchemesProcessor(), provider, tempDirManager.newPath("schemes"))

  @Test fun `load scheme`() {
    val localScheme = TestScheme("local")
    provider.write("$dirPath/local.xml", localScheme.serialize().toByteArray())

    val schemesManager = createSchemeManager(dirPath)
    schemesManager.loadSchemes()
    assertThat(schemesManager.allSchemes).containsOnly(localScheme)
  }

  @Test fun `load scheme with the same names`() {
    val localScheme = TestScheme("local")
    val data = localScheme.serialize().toByteArray()
    provider.write("$dirPath/local.xml", data)
    provider.write("$dirPath/local2.xml", data)

    val schemesManager = createSchemeManager(dirPath)
    schemesManager.loadSchemes()
    assertThat(schemesManager.allSchemes).containsOnly(localScheme)
  }

  @Test fun `load scheme from repo and read-only repo`() {
    val localScheme = TestScheme("local")

    provider.write("$dirPath/local.xml", localScheme.serialize().toByteArray())

    val remoteScheme = TestScheme("remote")
    val remoteRepository = tempDirManager.createRepository()
    remoteRepository
      .add("$dirPath/Mac OS X from RubyMine.xml", remoteScheme.serialize().toByteArray())
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemesManager = createSchemeManager(dirPath)
      schemesManager.loadSchemes()
      assertThat(schemesManager.allSchemes).containsOnly(remoteScheme, localScheme)
      assertThat(schemesManager.isMetadataEditable(localScheme)).isTrue()
      assertThat(schemesManager.isMetadataEditable(remoteScheme)).isFalse()
    }
  }

  @Test fun `scheme overrides read-only`() {
    val schemeName = "Emacs"
    val localScheme = TestScheme(schemeName, "local")

    provider.write("$dirPath/$schemeName.xml", localScheme.serialize().toByteArray())

    val remoteScheme = TestScheme(schemeName, "remote")
    val remoteRepository = tempDirManager.createRepository("remote")
    remoteRepository
      .add("$dirPath/$schemeName.xml", remoteScheme.serialize().toByteArray())
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemesManager = createSchemeManager(dirPath)
      schemesManager.loadSchemes()
      assertThat(schemesManager.allSchemes).containsOnly(localScheme)
      assertThat(schemesManager.isMetadataEditable(localScheme)).isFalse()
    }
  }

  inline fun Repository.useAsReadOnlySource(runnable: () -> Unit) {
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