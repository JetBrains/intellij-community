// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.testFramework.useProject
import com.intellij.testFramework.utils.module.assertModules
import com.intellij.ui.UIBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData

class MavenProjectWizardTest : MavenNewProjectWizardTestCase() {
  fun `test when module is created then its pom is unignored`() {
    // create project
    createProjectFromTemplate {
      it.baseData!!.name = "project"
      it.languageData!!.language = "Java"
      it.javaBuildSystemData!!.buildSystem = "Maven"
      it.javaMavenData!!.sdk = mySdk
    }.useProject { project ->
      // import project
      assertModules(project, "project")
      val module = project.modules.single()
      waitForMavenImporting(module)
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      // ignore pom
      val modulePomPath = "${project.basePath}/untitled/pom.xml"
      val ignoredPoms = listOf(modulePomPath)
      mavenProjectsManager.ignoredFilesPaths = ignoredPoms
      assertEquals(ignoredPoms, mavenProjectsManager.ignoredFilesPaths)

      // create module
      createModuleFromTemplate(project) {
        it.baseData!!.name = "untitled"
        it.languageData!!.language = "Java"
        it.javaBuildSystemData!!.buildSystem = "Maven"
        it.javaMavenData!!.sdk = mySdk
        it.javaMavenData!!.parentData = mavenProjectsManager.findProject(module)
      }
      assertModules(project, "project", "untitled")
      waitForMavenImporting(project.modules.single { it.name == "untitled" })

      // verify pom unignored
      assertSize(0, mavenProjectsManager.ignoredFilesPaths)
    }
  }

  fun `test new maven module inherits project sdk by default`() {
    // create project
    createProjectFromTemplate {
      it.baseData!!.name = "project"
      it.languageData!!.language = "Java"
      it.javaBuildSystemData!!.buildSystem = "Maven"
      it.javaMavenData!!.sdk = mySdk
    }.useProject { project ->
      // import project
      assertModules(project, "project")
      val module = project.modules.single()
      waitForMavenImporting(module)
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      // create module
      createModuleFromTemplate(project) {
        it.baseData!!.name = "untitled"
        it.languageData!!.language = "Java"
        it.javaBuildSystemData!!.buildSystem = "Maven"
        it.javaMavenData!!.sdk = mySdk
        it.javaMavenData!!.parentData = mavenProjectsManager.findProject(module)
      }
      assertModules(project, "project", "untitled")
      waitForMavenImporting(project.modules.single { it.name == "untitled" })

      // verify SKD is inherited
      val untitledModule = ModuleManager.getInstance(project).findModuleByName("untitled")!!
      val modifiableModel = ModuleRootManager.getInstance(untitledModule).modifiableModel
      assertTrue(modifiableModel.isSdkInherited)
    }
  }

  fun `test configurator creates module in project structure modifiable model`() {
    createProjectFromTemplate {
      it.baseData!!.name = "project"
      it.languageData!!.language = "Java"
      it.javaBuildSystemData!!.buildSystem = "Maven"
      it.javaMavenData!!.sdk = mySdk
    }.useProject { project ->
      assertModules(project, "project")
      waitForMavenImporting(project.modules.single())
      val mavenProjectsManager = MavenProjectsManager.getInstance(project)
      assertEquals(setOf("project"), mavenProjectsManager.projects.map { it.mavenId.artifactId }.toSet())

      val projectStructureConfigurable = ProjectStructureConfigurable.getInstance(project)
      val modulesConfigurator = projectStructureConfigurable.context.modulesConfigurator
      val addedModules = withWizard({ modulesConfigurator.addNewModule()!! }) {
        this as ProjectTypeStep
        assertTrue(setSelectedTemplate(UIBundle.message("label.project.wizard.module.generator.name"), null))
        val step = customStep as NewProjectWizardStep
        step.baseData!!.name = "untitled"
        step.languageData!!.language = "Java"
        step.javaBuildSystemData!!.buildSystem = "Maven"
        step.javaMavenData!!.sdk = mySdk
      }

      assertEquals(setOf("untitled"), addedModules.map { it.name }.toSet())

      val module = addedModules.single()
      waitForMavenImporting(module)

      assertEquals(setOf("project", "untitled"), modulesConfigurator.moduleModel.modules.map { it.name }.toSet())

      // verify there are no errors when the module is deleted
      val editor = modulesConfigurator.getModuleEditor(module)
      modulesConfigurator.deleteModules(listOf(editor))

      modulesConfigurator.apply()
    }
  }
}