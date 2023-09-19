// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.search

import com.intellij.maven.testFramework.MavenDomTestCase
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.refactoring.rename.RenameDialog
import org.junit.Test

class MavenModuleReferenceSearcherTest : MavenDomTestCase() {
  private fun renameDirectory(directory: PsiDirectory, newName: String) {
    val renameDialog = RenameDialog(myProject, directory, directory, null)
    renameDialog.performRename(newName)
  }

  @Test
  fun testDirectoryRenameModuleReferenceChanged() {
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
    importProject()

    val newModulePath = "m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newModulePath)

    val tag = findTag(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getText())
  }

  @Test
  fun testParentDirectoryRenameModuleReferenceChanged() {
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
    importProject()

    val newDirectoryName = "mNew"
    val newModulePath = "mNew/m1"

    val m1Parent = m1File.getParent().getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTag(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getText())
  }

  @Test
  fun testDirectoryRenameModuleRelativeReferenceChanged() {
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
    importProject()

    val newDirectoryName = "m1new"
    val newModulePath = "./m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTag(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getText())
  }

  @Test
  fun testDirectoryRenameModuleParentPathReferenceChanged() {
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
    importProject()

    val newDirectoryName = "m1new"
    val newModulePath = "../m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTag(parent2File, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getText())
  }

  @Test
  fun testDirectoryRenameModuleWeirdNameReferenceChanged() {
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
    importProject()

    val newDirectoryName = "module-new"
    val newModulePath = "module/module-new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTag(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getText())
  }

  @Test
  fun testParentDirectoryRenameModuleWeirdNameReferenceChanged() {
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
    importProject()

    val newDirectoryName = "module-new"
    val newModulePath = "module-new/module"

    val m1Parent = m1File.getParent().getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newDirectoryName)

    val tag = findTag(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getText())
  }


  @Test
  fun testIncorrectModuleNameWithNewLineRenameModuleReferenceChanged() {
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
    importProject()

    val newModulePath = "m1new"

    val m1Parent = m1File.getParent()
    val directory = PsiDirectoryFactory.getInstance(myProject).createDirectory(m1Parent)

    renameDirectory(directory, newModulePath.trim { it <= ' ' })

    val tag = findTag(parentFile, "project.modules.module")
    assertEquals(newModulePath, tag.getValue().getTrimmedText())
  }
}
