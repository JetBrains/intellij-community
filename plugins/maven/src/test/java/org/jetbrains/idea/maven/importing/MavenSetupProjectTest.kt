// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test
import java.nio.file.Files


class MavenSetupProjectTest : MavenSetupProjectTestCase() {

  @Test
  fun `test settings are not reset`() = runBlocking {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")
    waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync {
      assertProjectState(it, projectInfo)
      getGeneralSettings(it).isWorkOffline = true
      waitForImport {
        attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      assertProjectState(it, projectInfo, linkedProjectInfo)
      assertTrue(getGeneralSettings(it).isWorkOffline)
    }
  }

  @Test
  fun `test project open`() = runBlocking {
    val projectInfo = generateProject("A")
    waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync {
      assertProjectState(it, projectInfo)

    }
  }

  @Test
  fun `test project import`() = runBlocking {
    val projectInfo = generateProject("A")
    waitForImport {
      importProjectActionAsync(projectInfo.projectFile)
    }.useProjectAsync {
      assertProjectState(it, projectInfo)

    }
  }

  @Test
  fun `test project attach`() = runBlocking {
    val projectInfo = generateProject("A")
    openPlatformProjectAsync(projectInfo.projectFile.parent)
      .useProjectAsync {
        waitForImport {
          attachProjectAsync(it, projectInfo.projectFile)
        }
        assertProjectState(it, projectInfo)
      }
  }

  @Test
  fun `test project import from script`() = runBlocking {
    val projectInfo = generateProject("A")
    openPlatformProjectAsync(projectInfo.projectFile.parent)
      .useProjectAsync {
        waitForImport {
          attachProjectFromScriptAsync(it, projectInfo.projectFile)
        }
        assertProjectState(it, projectInfo)
      }
  }

  @Test
  fun `test module attach`() = runBlocking {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")
    waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync {
      assertProjectState(it, projectInfo)
      waitForImport {
        attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      assertProjectState(it, projectInfo, linkedProjectInfo)
    }
  }

  @Test
  fun `test project re-open`() = runBlocking {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")
    waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync(save = true) {
      assertProjectState(it, projectInfo)
      waitForImport {
        attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      assertProjectState(it, projectInfo, linkedProjectInfo)
    }
    openProjectAsync(projectInfo.projectFile)
      .useProjectAsync {
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }

  @Test
  fun `test project re-open with same module name in different cases`() = runBlocking {
    val projectPom = createModulePom("project-name", """
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <packaging>pom</packaging>
                     <version>1</version>
                     <modules>
                       <module>dir1/m</module>
                       <module>dir2/M</module>
                     </modules>
                     """.trimIndent())
    createModulePom("project-name/dir1/m", """
    <groupId>test</groupId>
    <artifactId>m</artifactId>
    <version>1</version>
    """.trimIndent())
    createModulePom("project-name/dir2/M", """
    <groupId>test</groupId>
    <artifactId>M</artifactId>
    <version>1</version>
    """.trimIndent())

    runBlocking {
      val projectInfo = ProjectInfo(projectPom, "project", "m (1)", "M (2)")
      waitForImport {
        openProjectAsync(projectInfo.projectFile)
      }.useProjectAsync(save = true) {
        assertProjectState(it, projectInfo)
      }
      openProjectAsync(projectInfo.projectFile)
        .useProjectAsync {
          assertProjectState(it, projectInfo)
        }
    }
  }

  @Test
  fun `test project re-import deprecation`() = runBlocking {
    val projectInfo = generateProject("A")
    val linkedProjectInfo = generateProject("L")

    waitForImport {
      openProjectAsync(projectInfo.projectFile)
    }.useProjectAsync(save = true) {
      assertProjectState(it, projectInfo)
      waitForImport {
        attachProjectAsync(it, linkedProjectInfo.projectFile)
      }
      assertProjectState(it, projectInfo, linkedProjectInfo)
    }
    importProjectActionAsync(projectInfo.projectFile)
      .useProjectAsync {
        assertProjectState(it, projectInfo, linkedProjectInfo)
      }
  }

  @Test
  fun `test workspace import forcibly enabled once per project`() = runBlocking {
    Registry.get("maven.legacy.import.available").setValue(true, testRootDisposable)
    val projectInfo = generateProject("A")

    MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
    val p1 = openProjectAsync(projectInfo.projectFile)
    p1.useProjectAsync(true) {
      // initial state: workspace import is disabled, has not been forced yet
      val mavenProjectsManager = MavenProjectsManager.getInstance(it)
      mavenProjectsManager.importingSettings.isWorkspaceImportForciblyTurnedOn = false
      waitForImportWithinTimeout(it) {
        mavenProjectsManager.importingSettings.isWorkspaceImportEnabled = false
        Unit
      }
    }

    val p2 = openProjectAsync(projectInfo.projectFile)
    p2.useProjectAsync(true) {
      // check that workspace import has been forced
      val mavenProjectsManager = MavenProjectsManager.getInstance(it)
      assertTrue(mavenProjectsManager.importingSettings.isWorkspaceImportEnabled)
      // user still chooses legacy import
      mavenProjectsManager.importingSettings.isWorkspaceImportEnabled = false
    }

    val projectFileDir = projectInfo.projectFile.parent.toNioPath()
    val moduleFilePath = appSystemDir
      .resolve("projects")
      .resolve(getProjectCacheFileName(projectFileDir))
      .resolve("external_build_system")
      .resolve("modules")
      .resolve("${projectFileDir.fileName}.xml")

    assertWithinTimeout(10) {
      assertTrue("Module file does not exist", Files.exists(moduleFilePath))
    }

    assertWithinTimeout(10) {
      assertTrue("Module file is empty", Files.size(moduleFilePath) > 0)
    }

    val p3 = openProjectAsync(projectInfo.projectFile)
    p3.useProjectAsync(true) {
      // check that workspace import has not been forced twice
      val mavenProjectsManager = MavenProjectsManager.getInstance(it)
      assertFalse(mavenProjectsManager.importingSettings.isWorkspaceImportEnabled)
    }
  }
}