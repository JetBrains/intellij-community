// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test


class MavenSetupProjectTest : MavenSetupProjectTestCase() {

  @Test
  fun `test settings are not reset`() = runBlocking {
    runBlocking {
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
  }

  @Test
  fun `test project open`() = runBlocking {
    runBlocking {
      val projectInfo = generateProject("A")
      waitForImport {
        openProjectAsync(projectInfo.projectFile)
      }.useProjectAsync {
        assertProjectState(it, projectInfo)

      }
    }
  }

  @Test
  fun `test project import`() = runBlocking {
    runBlocking {
      val projectInfo = generateProject("A")
      waitForImport {
        importProjectActionAsync(projectInfo.projectFile)
      }.useProjectAsync {
        assertProjectState(it, projectInfo)

      }
    }
  }

  @Test
  fun `test project attach`() = runBlocking {
    runBlocking {
      val projectInfo = generateProject("A")
      openPlatformProjectAsync(projectInfo.projectFile.parent)
        .useProjectAsync {
          waitForImport {
            attachProjectAsync(it, projectInfo.projectFile)
          }
          assertProjectState(it, projectInfo)
        }
    }
  }

  @Test
  fun `test project import from script`() = runBlocking {
    runBlocking {
      val projectInfo = generateProject("A")
      openPlatformProjectAsync(projectInfo.projectFile.parent)
        .useProjectAsync {
          waitForImport {
            attachProjectFromScriptAsync(it, projectInfo.projectFile)
          }
          assertProjectState(it, projectInfo)
        }
    }
  }

  @Test
  fun `test module attach`() = runBlocking {
    runBlocking {
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
  }

  @Test
  fun `test project re-open`() = runBlocking {
    runBlocking {
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
    runBlocking {
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
  }

  @Test
  fun `test workspace import forcibly enabled once per project`() = runBlocking {
    runBlocking {
      val projectInfo = generateProject("A")

      MavenProjectLegacyImporter.setAnswerToDeleteObsoleteModulesQuestion(true)
      waitForImport {
        openProjectAsync(projectInfo.projectFile)
      }.useProjectAsync(true) {
        // initial state: workspace import is disabled, has not been forced yet
        val mavenProjectsManager = MavenProjectsManager.getInstance(it)
        mavenProjectsManager.importingSettings.isWorkspaceImportForciblyTurnedOn = false
        mavenProjectsManager.importingSettings.isWorkspaceImportEnabled = false
      }

      waitForImport {
        openProjectAsync(projectInfo.projectFile)
      }.useProjectAsync(true) {
        // check that workspace import has been forced
        val mavenProjectsManager = MavenProjectsManager.getInstance(it)
        assertTrue(mavenProjectsManager.importingSettings.isWorkspaceImportEnabled)
        // user still chooses legacy import
        mavenProjectsManager.importingSettings.isWorkspaceImportEnabled = false
      }

      waitForImport {
        openProjectAsync(projectInfo.projectFile)
      }.useProjectAsync(true) {
        // check that workspace import has not been forced twice
        val mavenProjectsManager = MavenProjectsManager.getInstance(it)
        assertFalse(mavenProjectsManager.importingSettings.isWorkspaceImportEnabled)
      }
    }
  }
}