// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class WorkingWithOpenProjectTest : MavenMultiVersionImportingTestCase() {
  override fun setUp() = runBlocking {
    super.setUp()

    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testShouldNotFailOnNewEmptyPomCreation() = runBlocking {
    createModulePom("module", "") // should not throw an exception
    return@runBlocking
  }

  @Test
  fun testShouldNotFailOnAddingNewContentRootWithAPomFile() = runBlocking {
    val newRootDir = Path.of(dir.toString(), "newRoot")
    Files.createDirectories(newRootDir)

    val pomFile = newRootDir.resolve("pom.xml")
    Files.createFile(pomFile)

    val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newRootDir)

    PsiTestUtil.addContentRoot(getModule("project"), root!!) // should not throw an exception

    return@runBlocking
  }

  fun _testSavingAllDocumentBeforeReimport() = runBlocking {
    // cannot make it work die to order of document listeners

    projectsManager.listenForExternalChanges()
    val d = FileDocumentManager.getInstance().getDocument(projectPom)
    edtWriteAction {
      d!!.setText(createPomXml("""
                                <groupId>test</groupId>
                                <artifactId>project</artifactId>
                                <version>1</version>
                                <dependencies>
                                  <dependency>
                                    <groupId>junit</groupId>
                                    <artifactId>junit</artifactId>
                                    <version>4.0</version>
                                  </dependency>
                                </dependencies>
                                """.trimIndent()))
    }

    assertModuleLibDep("project", "Maven: junit:junit:4.0")
  }
}
