// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.IndexingTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

class MavenModuleReferenceSearcherTest : MavenDomTestCase() {
  private suspend fun renameDirectory(directory: PsiDirectory, newName: String) {
    // The rename refactoring resolves the <module> references via ReferencesSearch, which MavenModuleReferenceSearcher
    // answers by walking ModuleManager.getModules() and the MavenProjectsManager model. Make sure the import has fully
    // settled (workspace model committed, smart mode) before renaming, otherwise the reference is intermittently not
    // found and the <module> path is left unchanged (flaky).
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val renameDialog = RenameDialog(project, directory, directory, null)
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
    awaitConfiguration()
  }

  @Test
  fun testDirectoryRenameModuleReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newModulePath = "m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newModulePath)

    val tag = findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testParentDirectoryRenameModuleReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m/m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m/m1", """
                  <artifactId>m2</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newDirectoryName = "mNew"
    val newModulePath = "mNew/m1"

    val m1Parent = m1File.getParent().getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    //awaitConfiguration()

    val tag = findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testDirectoryRenameModuleRelativeReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>./m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newDirectoryName = "m1new"
    val newModulePath = "./m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testDirectoryRenameModuleParentPathReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>parent2</module>
                  </modules>
                  """.trimIndent())
    val parent2File = createModulePom("parent2", """
                  <groupId>group</groupId>
                  <artifactId>parent2</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>../m1</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent2</artifactId>
                    <version>1</version>
                    <relativePath>../parent2/pom.xml</relativePath>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newDirectoryName = "m1new"
    val newModulePath = "../m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTagValue(parent2File, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testDirectoryRenameModuleWeirdNameReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>module/module</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("module/module", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newDirectoryName = "module-new"
    val newModulePath = "module/module-new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }

  @Test
  fun testParentDirectoryRenameModuleWeirdNameReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>module/module</module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("module/module", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newDirectoryName = "module-new"
    val newModulePath = "module-new/module"

    val m1Parent = m1File.getParent().getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getText())
  }


  @Test
  fun testIncorrectModuleNameWithNewLineRenameModuleReferenceChanged() = runBlocking {
    val parentFile = createProjectPom("""
                  <groupId>group</groupId>
                  <artifactId>parent</artifactId>
                  <version>1</version>
                  <packaging>pom</packaging>
                  <modules>
                    <module>m1
                    </module>
                  </modules>
                  """.trimIndent())
    val m1File = createModulePom("m1", """
                  <artifactId>m1</artifactId>
                  <version>1</version>
                  <parent>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                  </parent>
                  """.trimIndent())
    importProjectAsync()

    val newModulePath = "m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(project).createDirectory(m1Parent)

    renameDirectory(directory, newModulePath.trim { it <= ' ' })

    val tag = findTagValue(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getTrimmedText())
  }
}
