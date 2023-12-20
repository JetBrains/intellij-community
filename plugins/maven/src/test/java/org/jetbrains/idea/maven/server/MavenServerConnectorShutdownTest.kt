// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.maven.testFramework.assertWithinTimeout
import com.intellij.openapi.util.Ref
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.withProjectAsync
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardTestCase

class MavenServerConnectorShutdownTest : MavenNewProjectWizardTestCase() {
  override fun runInDispatchThread() = false

  fun `test connector is shut down on project closing`() = runBlocking {
    val mavenServerManager = MavenServerManager.getInstance()
    val connectorRef = Ref<MavenServerConnector>()
    // create project
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
      }
    }.withProjectAsync {
      val connectors = mavenServerManager.allConnectors.filter { it.project?.name == "project" }
      assertEquals(1, connectors.size)
      connectorRef.set(connectors[0])
    }.closeProjectAsync()

    // our connector is shut down within short period
    assertWithinTimeout(5) {
      assertEquals(MavenServerConnector.State.STOPPED, connectorRef.get().state)
    }

    // there are no other connectors for the closed project
    val connectors = mavenServerManager.allConnectors.filter { it.project?.name == "project" }
    assertEquals(0, connectors.size)
  }
}