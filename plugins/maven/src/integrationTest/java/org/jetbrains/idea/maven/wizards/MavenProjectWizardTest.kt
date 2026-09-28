// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.maven.testFramework.fixtures.assertSize
import com.intellij.maven.testFramework.fixtures.mavenProjectWizardFixture
import com.intellij.maven.testFramework.fixtures.sdk
import com.intellij.maven.testFramework.fixtures.waitForModuleCreation
import com.intellij.maven.testFramework.fixtures.waitForProjectCreation
import com.intellij.maven.testFramework.fixtures.withWizard
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.useProjectAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class MavenProjectWizardTest {
  private val maven by mavenProjectWizardFixture()

  @Test
  @RegistryKey(key = "ide.activity.tracking.enable.debug", value = "true")
  @SystemProperty(propertyKey = "idea.force.commit.on.external.change", propertyValue = "true")
  fun `test when module is created then its pom is unignored`() = runBlocking {
    // create project
    maven.waitForProjectCreation {
      maven.wizards.createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = maven.sdk
      }
    }.useProjectAsync { project ->
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      // import project
      assertModules(project, "project")
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      // ignore pom
      val modulePomPath = "${project.basePath}/untitled/pom.xml"
      val ignoredPoms = listOf(modulePomPath)
      mavenProjectsManager.ignoredFilesPaths = ignoredPoms
      assertEquals(ignoredPoms, mavenProjectsManager.ignoredFilesPaths)

      // create module
      maven.waitForModuleCreation {
        maven.wizards.createModuleFromTemplate(project, JAVA) {
          it.baseData!!.name = "untitled"
          it.javaBuildSystemData!!.buildSystem = MAVEN
          it.javaMavenData!!.sdk = maven.sdk
          it.javaMavenData!!.parentData = null
        }
      }
      assertModules(project, "project", "untitled")

      // verify pom unignored
      assertSize(0, mavenProjectsManager.ignoredFilesPaths)
    }
  }

  @Test
  fun `test new maven module inherits project sdk by default`() = runBlocking {
    // create project
    maven.waitForProjectCreation {
      maven.wizards.createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = maven.sdk
      }
    }.useProjectAsync { project ->
      // import project
      assertModules(project, "project")
      val module = project.modules.single()
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      // create
      maven.waitForModuleCreation {
        maven.wizards.createModuleFromTemplate(project, JAVA) {
          it.baseData!!.name = "untitled"
          it.javaBuildSystemData!!.buildSystem = MAVEN
          it.javaMavenData!!.sdk = maven.sdk
          it.javaMavenData!!.parentData = mavenProjectsManager.findProject(module)
        }
      }
      assertModules(project, "project", "untitled")

      // verify SKD is inherited
      val untitledModule = ModuleManager.getInstance(project).findModuleByName("untitled")!!
      val modifiableModel = ModuleRootManager.getInstance(untitledModule).modifiableModel
      assertTrue(modifiableModel.isSdkInherited)
    }
  }

  @Test
  fun `test configurator creates module in project structure modifiable model`() = runBlocking {
    maven.waitForProjectCreation {
      maven.wizards.createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = maven.sdk
      }
    }.useProjectAsync { project ->
      assertModules(project, "project")
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      val projectStructureConfigurable = ProjectStructureConfigurable.getInstance(project)
      val modulesConfigurator = projectStructureConfigurable.context.modulesConfigurator
      val module = maven.waitForModuleCreation {
        withContext(Dispatchers.EDT) {
          writeIntentReadAction {
            maven.withWizard({ modulesConfigurator.addNewModule(null)!! }) {
              this as ProjectTypeStep
              assertTrue(setSelectedTemplate(JAVA, null))
              val step = customStep as NewProjectWizardStep
              step.baseData!!.name = "untitled"
              step.javaBuildSystemData!!.buildSystem = MAVEN
              step.javaMavenData!!.sdk = maven.sdk
            }.single()
          }
        }
      }

      assertEquals(setOf("project", "untitled"), modulesConfigurator.moduleModel.modules.map { it.name }.toSet())

      // verify there are no errors when the module is deleted
      val editor = modulesConfigurator.getModuleEditor(module)
      withContext(Dispatchers.EDT) {
        modulesConfigurator.deleteModules(listOf(editor))
        modulesConfigurator.apply()
      }
    }
  }
}
