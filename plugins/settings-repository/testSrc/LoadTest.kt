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

import com.intellij.configurationStore.*
import com.intellij.openapi.components.RoamingType
import org.eclipse.jgit.lib.Repository
import org.hamcrest.CoreMatchers.equalTo
import org.jetbrains.settingsRepository.ReadonlySource
import org.jetbrains.settingsRepository.git.cloneBare
import org.jetbrains.settingsRepository.git.commit
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File

class LoadTest : TestCase() {
  private val dirPath = "\$ROOT_CONFIG$/keymaps"

  private fun createSchemeManager(dirPath: String) = SchemeManagerImpl<TestScheme, TestScheme>(dirPath, TestSchemesProcessor(), RoamingType.PER_USER, provider, tempDirManager.newDirectory("schemes"))

  public Test fun `load scheme`() {
    val localScheme = TestScheme("local")
    val data = localScheme.serialize().toByteArray()
    provider.save("$dirPath/local.xml", data)

    val schemesManager = createSchemeManager(dirPath)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes(), equalTo(listOf(localScheme)))
  }

  public Test fun `load scheme with the same names`() {
    val localScheme = TestScheme("local")
    val data = localScheme.serialize().toByteArray()
    provider.save("$dirPath/local.xml", data)
    provider.save("$dirPath/local2.xml", data)

    val schemesManager = createSchemeManager(dirPath)
    schemesManager.loadSchemes()
    assertThat(schemesManager.getAllSchemes(), equalTo(listOf(localScheme)))
  }

  public Test fun `load scheme from repo and read-only repo`() {
    val localScheme = TestScheme("local")

    provider.save("$dirPath/local.xml", localScheme.serialize().toByteArray())

    val remoteScheme = TestScheme("remote")
    val remoteRepository = tempDirManager.createRepository()
    remoteRepository
      .add(remoteScheme.serialize().toByteArray(), "$dirPath/Mac OS X from RubyMine.xml")
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemesManager = createSchemeManager(dirPath)
      schemesManager.loadSchemes()
      assertThat(schemesManager.getAllSchemes(), equalTo(listOf(remoteScheme, localScheme)))
      assertThat(schemesManager.isMetadataEditable(localScheme), equalTo(true))
      assertThat(schemesManager.isMetadataEditable(remoteScheme), equalTo(false))
    }
  }

  public Test fun `scheme overrides read-only`() {
    val schemeName = "Emacs"
    val localScheme = TestScheme(schemeName, "local")

    provider.save("$dirPath/$schemeName.xml", localScheme.serialize().toByteArray())

    val remoteScheme = TestScheme(schemeName, "remote")
    val remoteRepository = tempDirManager.createRepository("remote")
    remoteRepository
      .add(remoteScheme.serialize().toByteArray(), "$dirPath/$schemeName.xml")
      .commit("")

    remoteRepository.useAsReadOnlySource {
      val schemesManager = createSchemeManager(dirPath)
      schemesManager.loadSchemes()
      assertThat(schemesManager.getAllSchemes(), equalTo(listOf(localScheme)))
      assertThat(schemesManager.isMetadataEditable(localScheme), equalTo(false))
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
    val source = ReadonlySource(getWorkTree().getAbsolutePath())
    assertThat(cloneBare(source.url!!, File(icsManager.readOnlySourcesManager.rootDir, source.path!!)).getObjectDatabase().exists(), equalTo(true))
    icsManager.readOnlySourcesManager.setSources(listOf(source))
    return source
  }
}