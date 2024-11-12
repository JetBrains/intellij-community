// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.JAVA
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.module.assertModules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.junit.jupiter.api.Assertions
import java.io.File

class MavenProjectWizardTest : MavenNewProjectWizardTestCase() {
  override fun runInDispatchThread() = false

  fun `test when module is created then its pom is unignored`() = runBlocking {
    Registry.get("ide.activity.tracking.enable.debug").setValue(true, testRootDisposable)
    setSystemProperty("idea.force.commit.on.external.change", "true", testRootDisposable)

    // create project
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
      }
    }.useProjectAsync { project ->
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      // import project
      assertModules(project, "project")
      //val module = project.modules.single()
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      // ignore pom
      val modulePomPath = "${project.basePath}/untitled/pom.xml"
      val ignoredPoms = listOf(modulePomPath)
      mavenProjectsManager.ignoredFilesPaths = ignoredPoms
      assertEquals(ignoredPoms, mavenProjectsManager.ignoredFilesPaths)

      // create module
      waitForModuleCreation {
        createModuleFromTemplate(project, JAVA) {
          it.baseData!!.name = "untitled"
          it.javaBuildSystemData!!.buildSystem = MAVEN
          it.javaMavenData!!.sdk = mySdk
          it.javaMavenData!!.parentData = mavenProjectsManager.findProject(module)
        }
      }
      assertModules(project, "project", "untitled")

      // verify pom unignored
      assertSize(0, mavenProjectsManager.ignoredFilesPaths)
    }
  }

  fun `test new maven module inherits project sdk by default`() = runBlocking {
    // create project
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
      }
    }.useProjectAsync { project ->
      // import project
      assertModules(project, "project")
      val module = project.modules.single()
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      // create
      waitForModuleCreation {
        createModuleFromTemplate(project, JAVA) {
          it.baseData!!.name = "untitled"
          it.javaBuildSystemData!!.buildSystem = MAVEN
          it.javaMavenData!!.sdk = mySdk
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

  fun `test configurator creates module in project structure modifiable model`() = runBlocking {
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
      }
    }.useProjectAsync { project ->
      assertModules(project, "project")
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      val projectStructureConfigurable = ProjectStructureConfigurable.getInstance(project)
      val modulesConfigurator = projectStructureConfigurable.context.modulesConfigurator
      val module = waitForModuleCreation {
        withWizard({ modulesConfigurator.addNewModule(null)!! }) {
          this as ProjectTypeStep
          assertTrue(setSelectedTemplate(JAVA, null))
          val step = customStep as NewProjectWizardStep
          step.baseData!!.name = "untitled"
          step.javaBuildSystemData!!.buildSystem = MAVEN
          step.javaMavenData!!.sdk = mySdk
        }.single()
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

  fun testCreateNewProject() = runBlocking {
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
        it.javaMavenData!!.groupId = "com.example"
      }
    }.useProjectAsync { project ->
      assertModules(project, "project")
      assertCorrectProjectFiles("data/createNewProject", project)
    }
  }

  fun testCreateNewProjectWithCustomGroupAndArtifactId() = runBlocking {
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
        it.javaMavenData!!.groupId = "com.example"
        it.javaMavenData!!.artifactId = "myproject"
      }
    }.useProjectAsync { project ->
      assertModules(project, "myproject")
      assertCorrectProjectFiles("data/createNewProjectWithCustomGroupAndArtifactId", project)
    }
  }

  fun testCreateNewModule() = runBlocking {
    waitForProjectCreation {
      createProjectFromTemplate(JAVA) {
        it.baseData!!.name = "project"
        it.javaBuildSystemData!!.buildSystem = MAVEN
        it.javaMavenData!!.sdk = mySdk
        it.javaMavenData!!.groupId = "com.example"
        it.javaMavenData!!.artifactId = "project"
      }
    }.useProjectAsync { project ->
      assertModules(project, "project")
      runBlocking {
        waitForModuleCreation {
          createModuleFromTemplate(project, JAVA) {
            it.baseData!!.name = "module"
            it.javaBuildSystemData!!.buildSystem = MAVEN
            it.javaMavenData!!.sdk = mySdk
            it.javaMavenData!!.parentData = MavenProjectsManager.getInstance(project).findProject(project.modules.single())
          }
        }
      }
      assertModules(project, "project", "module")
      assertCorrectProjectFiles("data/createNewModule", project)
    }
  }


  private fun assertCorrectProjectFiles(testDataPath: String, project: Project) {
    val testDataFolder = File(testDataPath)
    val basePath = File(project.basePath!!)
    var foundExpectedFiles = 0
    testDataFolder.walkTopDown().forEach {
      if (!it.isFile) return@forEach
      foundExpectedFiles++
      val relativePath = it.relativeTo(testDataFolder).toPath().toString()
      val pathInProject = File(basePath, relativePath)
      Assertions.assertTrue(
        pathInProject.exists() && pathInProject.isFile,
        "Expected ${it.name} file to exist in output, but it could not be found."
      )
      assertEqualsToFile("Expected correct file after generation", it, pathInProject.readText())
    }
  }
}
