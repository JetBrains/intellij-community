// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.fixtures.findTagValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenModuleReferenceSearcherTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private suspend fun renameDirectory(directory: PsiDirectory, newName: String) {
    maven.awaitConfiguration()
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        // RenameProcessor is not dumb-aware: BaseRefactoringProcessor.doRun() cancels the rename without an error when
        // the project is dumb, and the <module> path stays unchanged. The import queues the scan from the event queue,
        // so a wait outside the EDT can return before the scan starts. This wait dispatches the queued events and
        // returns in smart mode with an empty event queue. The write-intent lock is kept until the rename starts, so
        // no scan can start in between.
        IndexingTestUtil.waitUntilIndexesAreReady(maven.project)
        val renameDialog = RenameDialog(maven.project, directory, directory, null)
        try {
          renameDialog.performRename(newName)
        }
        finally {
          renameDialog.close()
        }
      }
      // The rename edits the <module> reference in the parent pom's in-memory document. Flush all documents to disk
      // before the background re-import refreshes the poms from VFS, otherwise the unsaved-memory-vs-disk divergence
      // makes MemoryDiskConflictResolver throw in tests.
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    maven.awaitConfiguration()
  }

  @Test
  fun testDirectoryRenameModuleReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newModulePath = "m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newModulePath)

    val tag = maven.findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testParentDirectoryRenameModuleReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m/m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m/m1", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newDirectoryName = "mNew"
    val newModulePath = "mNew/m1"

    val m1Parent = m1File.getParent().getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    //maven.awaitConfiguration()

    val tag = maven.findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testDirectoryRenameModuleRelativeReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>./m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newDirectoryName = "m1new"
    val newModulePath = "./m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = maven.findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testDirectoryRenameModuleParentPathReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>parent2</module>
                  </modules>
                  """.trimIndent())
    val parent2File = maven.createModulePom("parent2", """
                  <groupId>group</groupId>
                  <artifactId>parent2</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>../m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent2</artifactId>
                    <version>1</version>
                    <relativePath>../parent2/pom.xml</relativePath>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newDirectoryName = "m1new"
    val newModulePath = "../m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = maven.findTagValue(parent2File, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testDirectoryRenameModuleWeirdNameReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>module/module</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("module/module", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newDirectoryName = "module-new"
    val newModulePath = "module/module-new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = maven.findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testParentDirectoryRenameModuleWeirdNameReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>module/module</module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("module/module", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newDirectoryName = "module-new"
    val newModulePath = "module-new/module"

    val m1Parent = m1File.getParent().getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = maven.findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }


  @Test
  fun testIncorrectModuleNameWithNewLineRenameModuleReferenceChanged() = runBlocking {
    val parentFile = maven.createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1
                    </module>
                  </modules>
                  """.trimIndent())
    val m1File = maven.createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    maven.importProjectAsync()

    val newModulePath = "m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(maven.project).createDirectory(m1Parent)

    renameDirectory(directory, newModulePath.trim { it <= ' ' })

    val tag = maven.findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getTrimmedText())
  }
}
