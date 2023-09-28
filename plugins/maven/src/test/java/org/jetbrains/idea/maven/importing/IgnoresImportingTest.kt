/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class IgnoresImportingTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() {
    super.setUp()
    initProjectsManager(false)
  }

  @Test
  fun testDoNotImportIgnoredProjects() = runBlocking {
    val p1 = createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    val p2 = createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    importProjects(p1, p2)
    assertModules("project2")
  }

  @Test
  fun testAddingAndRemovingModulesWhenIgnoresChange() = runBlocking {
    val p1 = createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    val p2 = createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    importProjects(p1, p2)
    assertModules("project1", "project2")

    setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    doReadAndImport()
    assertModules("project2")

    setIgnoredFilesPathForNextImport(listOf(p2.getPath()))
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    doReadAndImport()
    assertModules("project1")

    setIgnoredFilesPathForNextImport(emptyList<String>())
    doReadAndImport()
    assertModules("project1", "project2")
  }

  @Test
  fun testDoNotAskTwiceToRemoveIgnoredModule() = runBlocking {
    if (!supportsKeepingModulesFromPreviousImport()) return@runBlocking

    val p1 = createModulePom("project1",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project1</artifactId>
                                       <version>1</version>
                                       """.trimIndent())

    val p2 = createModulePom("project2",
                             """
                                       <groupId>test</groupId>
                                       <artifactId>project2</artifactId>
                                       <version>1</version>
                                       """.trimIndent())
    importProjects(p1, p2)
    assertModules("project1", "project2")

    setIgnoredFilesPathForNextImport(listOf(p1.getPath()))
    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(false)
    doReadAndImport()

    assertModules("project1", "project2")

    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(false)
    doReadAndImport()

    assertModules("project1", "project2")
  }

  private suspend fun doReadAndImport() {
    if (isNewImportingProcess) {
      doImportProjects(projectsManager.getProjectsTree().getExistingManagedFiles(), true)
    }
    else {
      updateAllProjects()
    }
  }
}
