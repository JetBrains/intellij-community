// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.runBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.ProjectInfo
import org.jetbrains.idea.maven.fixtures.assertProjectState
import org.jetbrains.idea.maven.fixtures.attachProjectAsync
import org.jetbrains.idea.maven.fixtures.attachProjectFromScriptAsync
import com.intellij.maven.testFramework.fixtures.createModulePom
import org.jetbrains.idea.maven.fixtures.generateProject
import org.jetbrains.idea.maven.fixtures.getGeneralSettings
import org.jetbrains.idea.maven.fixtures.importProjectActionAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import org.jetbrains.idea.maven.fixtures.openPlatformProjectAsync
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import org.jetbrains.idea.maven.fixtures.waitForImport
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource


@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenSetupProjectTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  

  @Test
  fun `test settings are not reset`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    val linkedProjectInfo = maven.generateProject("L")
    maven.waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync {
      maven.assertProjectState(it, projectInfo)
      maven.getGeneralSettings(it).isWorkOffline = true
      maven.waitForImport {
        maven.attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      maven.assertProjectState(it, projectInfo, linkedProjectInfo)
      assertTrue(maven.getGeneralSettings(it).isWorkOffline)
    }
  }

  @Test
  fun `test project open`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    maven.waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync {
      maven.assertProjectState(it, projectInfo)
    }
  }

  @Test
  fun `test project import`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    maven.waitForImport {
      maven.importProjectActionAsync(projectInfo.projectFile)
    }.useProjectAsync {
      maven.assertProjectState(it, projectInfo)
    }
  }

  @Test
  fun `test project attach`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    maven.openPlatformProjectAsync(projectInfo.projectFile.parent)
      .useProjectAsync {
        maven.waitForImport {
          maven.attachProjectAsync(it, projectInfo.projectFile)
        }
        maven.assertProjectState(it, projectInfo)
      }
  }

  @Test
  fun `test project import from script`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    maven.openPlatformProjectAsync(projectInfo.projectFile.parent)
      .useProjectAsync {
        maven.waitForImport {
          maven.attachProjectFromScriptAsync(it, projectInfo.projectFile)
        }
        maven.assertProjectState(it, projectInfo)
      }
  }

  @Test
  fun `test module attach`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    val linkedProjectInfo = maven.generateProject("L")
    maven.waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync {
      maven.assertProjectState(it, projectInfo)
      maven.waitForImport {
        maven.attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      maven.assertProjectState(it, projectInfo, linkedProjectInfo)
    }
  }

  @Test
  fun `test project re-open`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    val linkedProjectInfo = maven.generateProject("L")

    WorkspaceModelCacheImpl.forceEnableCaching(maven.testRootDisposable)
    maven.waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync(save = true) {
      maven.assertProjectState(it, projectInfo)
      maven.waitForImport {
        maven.attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      maven.assertProjectState(it, projectInfo, linkedProjectInfo)
    }
    openProjectAsync(projectInfo.projectFile)
      .useProjectAsync {
        maven.assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }

  @Test
  fun `test project re-open with same module name in different cases`() = runBlocking {
    val projectPom = maven.createModulePom("project-name", """
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <packaging>pom</packaging>
                     <version>1</version>
                     <modules>
                       <module>dir1/m</module>
                       <module>dir2/M</module>
                     </modules>
                     """.trimIndent())
    maven.createModulePom("project-name/dir1/m", """
    <groupId>test</groupId>
    <artifactId>m</artifactId>
    <version>1</version>
    """.trimIndent())
    maven.createModulePom("project-name/dir2/M", """
    <groupId>test</groupId>
    <artifactId>M</artifactId>
    <version>1</version>
    """.trimIndent())

    runBlocking {
      val projectInfo = ProjectInfo(projectPom, "project", "m (1)", "M (2)")

      WorkspaceModelCacheImpl.forceEnableCaching(maven.testRootDisposable)
      maven.waitForImport {
        openProjectAsync(projectInfo.projectFile)
      }.useProjectAsync(save = true) {
        maven.assertProjectState(it, projectInfo)
      }
      openProjectAsync(projectInfo.projectFile)
        .useProjectAsync {
          maven.assertProjectState(it, projectInfo)
        }
    }
  }

  @Test
  fun `test project re-import deprecation`() = runBlocking {
    val projectInfo = maven.generateProject("A")
    val linkedProjectInfo = maven.generateProject("L")

    WorkspaceModelCacheImpl.forceEnableCaching(maven.testRootDisposable)
    maven.waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync(save = true) {
      maven.assertProjectState(it, projectInfo)
      maven.waitForImport {
        maven.attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      maven.assertProjectState(it, projectInfo, linkedProjectInfo)
    }
    maven.importProjectActionAsync(projectInfo.projectFile)
      .useProjectAsync {
        maven.assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }
}