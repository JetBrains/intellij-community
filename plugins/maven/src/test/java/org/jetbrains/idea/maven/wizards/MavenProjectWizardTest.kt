// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.module.assertModules
import com.intellij.testFramework.withProjectAsync
import com.intellij.ui.UIBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.buildtool.MavenImportSpec
import org.jetbrains.idea.maven.importing.MavenProjectImporter.Companion.isImportToWorkspaceModelEnabled
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.junit.Assume.assumeTrue

class MavenProjectWizardTest : MavenNewProjectWizardTestCase() {
  override fun runInDispatchThread() = false

  private fun isWorkspaceImport(): Boolean {
    return isImportToWorkspaceModelEnabled(myProject)
  }

  fun `test when module is created then its pom is unignored`() = runBlocking {
    assumeTrue(isWorkspaceImport())
    // create project
    waitForProjectCreation {
      createProjectFromTemplate {
        it.baseData!!.name = "project"
        it.languageData!!.language = "Java"
        it.javaBuildSystemData!!.buildSystem = "Maven"
        it.javaMavenData!!.sdk = mySdk
      }
    }.withProjectAsync { project ->
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
        createModuleFromTemplate(project) {
          it.baseData!!.name = "untitled"
          it.languageData!!.language = "Java"
          it.javaBuildSystemData!!.buildSystem = "Maven"
          it.javaMavenData!!.sdk = mySdk
          it.javaMavenData!!.parentData = mavenProjectsManager.findProject(module)
        }
      }
      assertModules(project, "project", "untitled")

      // verify pom unignored
      assertSize(0, mavenProjectsManager.ignoredFilesPaths)
    }
    return@runBlocking
  }

  fun `test new maven module inherits project sdk by default`() = runBlocking {
    assumeTrue(isWorkspaceImport())
    // create project
    waitForProjectCreation {
      createProjectFromTemplate {
        it.baseData!!.name = "project"
        it.languageData!!.language = "Java"
        it.javaBuildSystemData!!.buildSystem = "Maven"
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
        createModuleFromTemplate(project) {
          it.baseData!!.name = "untitled"
          it.languageData!!.language = "Java"
          it.javaBuildSystemData!!.buildSystem = "Maven"
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
    return@runBlocking
  }

  fun `test configurator creates module in project structure modifiable model`() = runBlocking {
    assumeTrue(isWorkspaceImport())
    waitForProjectCreation {
      createProjectFromTemplate {
        it.baseData!!.name = "project"
        it.languageData!!.language = "Java"
        it.javaBuildSystemData!!.buildSystem = "Maven"
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
          assertTrue(setSelectedTemplate(UIBundle.message("label.project.wizard.module.generator.name"), null))
          val step = customStep as NewProjectWizardStep
          step.baseData!!.name = "untitled"
          step.languageData!!.language = "Java"
          step.javaBuildSystemData!!.buildSystem = "Maven"
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
    return@runBlocking
  }

  private suspend fun waitForProjectCreation(createProject: () -> Project): Project {
    val project = withContext(Dispatchers.EDT) { createProject() }

    // hack to wait for all the pending updates on newly created project
    MavenProjectsManager.getInstance(project).updateAllMavenProjects(MavenImportSpec(false, false, false))

    return project
  }

  private suspend fun waitForModuleCreation(createModule: () -> Module): Module {
    val module = withContext(Dispatchers.EDT) { createModule() }

    // hack to wait for all the pending updates
    MavenProjectsManager.getInstance(module.project).updateAllMavenProjects(MavenImportSpec(false, false, false))

    return module
  }
}