// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.assertModuleLibDep
import org.jetbrains.idea.maven.fixtures.createModulePom
import org.jetbrains.idea.maven.fixtures.createPomXml
import org.jetbrains.idea.maven.fixtures.getModule
import org.jetbrains.idea.maven.fixtures.importProjectAsync
import org.jetbrains.idea.maven.fixtures.mavenImportingFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.api.BeforeEach

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class WorkingWithOpenProjectTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @BeforeEach
  fun setUp(): Unit = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    """.trimIndent())
  }

  @Test
  fun testShouldNotFailOnNewEmptyPomCreation() = runBlocking {
    maven.createModulePom("module", "") // should not throw an exception
    return@runBlocking
  }

  @Test
  fun testShouldNotFailOnAddingNewContentRootWithAPomFile() = runBlocking {
    val newRootDir = Path.of(maven.dir.toString(), "newRoot")
    Files.createDirectories(newRootDir)

    val pomFile = newRootDir.resolve("pom.xml")
    Files.createFile(pomFile)

    val root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(newRootDir)

    PsiTestUtil.addContentRoot(maven.getModule("project"), root!!) // should not throw an exception

    return@runBlocking
  }

  fun _testSavingAllDocumentBeforeReimport() = runBlocking {
    // cannot make it work die to order of document listeners

    maven.projectsManager.listenForExternalChanges()
    val d = FileDocumentManager.getInstance().getDocument(maven.projectPom)
    edtWriteAction {
      d!!.setText(maven.createPomXml("""
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

    maven.assertModuleLibDep("project", "Maven: junit:junit:4.0")
  }
}
