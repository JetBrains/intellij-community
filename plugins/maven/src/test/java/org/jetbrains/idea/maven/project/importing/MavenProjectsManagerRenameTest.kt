// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.testFramework.UsefulTestCase.assertSameElements
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertModules
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.fixtures.renameModule
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenProjectsManagerRenameTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  
  @Test
  fun `rename compound module`() = runBlocking {
    maven.createProjectPom("""
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
    maven.importProjectAsync()
    maven.assertModules("project", "project.main", "project.test")

    maven.renameModule("project", "group.prefix.project")
    maven.assertModules("group.prefix.project", "project.main", "project.test")

    // incremental sync doesn't update module if effective pom dependencies haven't changed
    maven.updateAllProjectsFullSync()
    maven.assertModules("group.prefix.project", "group.prefix.project.main", "group.prefix.project.test")
  }

  @Test
  fun `rename compound module - main and test`() = runBlocking {
    maven.createProjectPom("""
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
    maven.importProjectAsync()
    maven.assertModules("project", "project.main", "project.test")

    maven.renameModule("project.main", "project.verymain")
    maven.renameModule("project.test", "project.verytest")
    maven.assertModules("project", "project.verymain", "project.verytest")

    // incremental sync doesn't update module if effective pom dependencies haven't changed
    maven.updateAllProjectsFullSync()
    val moduleNames = WorkspaceModel.getInstance(maven.project).currentSnapshot.entities(ModuleEntity::class.java).map { it.name }.toSet()
    assertSameElements(moduleNames, "project", "project.main", "project.test")
    maven.assertModules("project", "project.main", "project.test")
  }
}