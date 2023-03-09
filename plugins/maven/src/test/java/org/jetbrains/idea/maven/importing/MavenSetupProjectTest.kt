// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing

import com.intellij.openapi.externalSystem.util.openPlatformProjectAsync
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenSetupProjectTest : MavenSetupProjectTestCase() {

  @Test
  fun `test settings are not reset`() {
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
  fun `test project open`() {
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
  fun `test project import`() {
    runBlocking {
      val projectInfo = generateProject("A")
      waitForImport {
        importProjectAsync(projectInfo.projectFile)
      }.useProjectAsync {
        assertProjectState(it, projectInfo)
      }
    }
  }

  @Test
  fun `test project attach`() {
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
  fun `test project import from script`() {
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
  fun `test module attach`() {
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
  fun `test project re-open`() {
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
  fun `test project re-import deprecation`() {
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
      importProjectAsync(projectInfo.projectFile)
        .useProjectAsync {
          assertProjectState(it, projectInfo, linkedProjectInfo)
        }
    }
  }
}