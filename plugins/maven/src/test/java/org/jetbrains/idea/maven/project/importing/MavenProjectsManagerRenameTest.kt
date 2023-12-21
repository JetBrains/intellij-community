// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

class MavenProjectsManagerRenameTest : MavenMultiVersionImportingTestCase() {
  
  @Test
  fun `rename compound module`() = runBlocking {
    assumeTrue(isWorkspaceImport)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                           <maven.compiler.source>17</maven.compiler.source>
                           <maven.compiler.target>17</maven.compiler.target>
                           <maven.compiler.testSource>11</maven.compiler.testSource>
                           <maven.compiler.testTarget>11</maven.compiler.testTarget>
                       </properties>
                       """.trimIndent())
    importProjectAsync()
    assertModules("project", "project.main", "project.test")

    renameModule("project", "group.prefix.project")
    assertModules("group.prefix.project", "project.main", "project.test")

    updateAllProjects()
    assertModules("group.prefix.project", "group.prefix.project.main", "group.prefix.project.test")
  }

  @Test
  fun `rename compound module - main and test`() = runBlocking {
    assumeTrue(isWorkspaceImport)
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                           <maven.compiler.source>17</maven.compiler.source>
                           <maven.compiler.target>17</maven.compiler.target>
                           <maven.compiler.testSource>11</maven.compiler.testSource>
                           <maven.compiler.testTarget>11</maven.compiler.testTarget>
                       </properties>
                       """.trimIndent())
    importProjectAsync()
    assertModules("project", "project.main", "project.test")

    renameModule("project.main", "project.verymain")
    renameModule("project.test", "project.verytest")
    assertModules("project", "project.verymain", "project.verytest")

    updateAllProjects()
    val moduleNames = WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java).map { it.name }.toSet()
    assertSameElements(moduleNames, "project", "project.main", "project.test")
    assertModules("project", "project.main", "project.test")
  }
}