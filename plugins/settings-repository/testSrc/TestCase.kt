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

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.FixtureRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TemporaryDirectory
import org.eclipse.jgit.lib.Repository
import org.jetbrains.jgit.dirCache.AddFile
import org.jetbrains.jgit.dirCache.edit
import org.jetbrains.settingsRepository.IcsManager
import org.jetbrains.settingsRepository.git
import org.junit.Rule
import org.junit.rules.TestRule
import java.io.File
import kotlin.properties.Delegates

val testDataPath: String = "${PlatformTestUtil.getCommunityPath()}/plugins/settings-repository/testData"

fun StreamProvider.save(path: String, data: ByteArray) {
  saveContent(path, data, data.size(), RoamingType.PER_USER)
}

fun Repository.add(data: ByteArray, path: String): Repository {
  FileUtil.writeToFile(File(getWorkTree(), path), data)
  edit(AddFile(path))
  return this
}

abstract class TestCase {
  val fixtureManager = FixtureRule()

  val tempDirManager = TemporaryDirectory()
  public Rule fun getTemporaryFolder(): TemporaryDirectory = tempDirManager

  public Rule fun getFixtureRule(): TestRule = fixtureManager

  val icsManager by Delegates.lazy {
    val icsManager = IcsManager(tempDirManager.newDirectory())
    icsManager.repositoryManager.createRepositoryIfNeed()
    icsManager.repositoryActive = true
    icsManager
  }

  val provider by Delegates.lazy { icsManager.ApplicationLevelProvider() }
}

fun TemporaryDirectory.createRepository(directoryName: String? = null) = git.createRepository(newDirectory(directoryName))
