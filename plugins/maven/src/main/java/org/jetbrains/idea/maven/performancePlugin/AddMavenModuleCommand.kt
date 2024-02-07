// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
//import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeItem
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizard
//import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizardData.Companion.archetypeMavenData

class AddMavenModuleCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "addMavenModule"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val project = context.project
    val (moduleName, parentModuleName) = extractCommandArgument(PREFIX).split("|")
    val modules = ModuleManager.getInstance(project).modules
    val parenModule = modules.firstOrNull { it.name == parentModuleName }
    val projectPath = parenModule?.moduleNioFile?.parent?.let { "${it}/$moduleName" } ?: "${project.basePath}/$moduleName"
    val moduleBuilder = ModuleBuilder.getAllBuilders().first { it::class == MavenArchetypeNewProjectWizard.Builder::class } as MavenArchetypeNewProjectWizard.Builder
    val disposer = Disposer.newDisposable()
    val wizardContext = WizardContext(project, disposer)
      .apply {
        //projectJdk = ...
        projectName = moduleName
        setProjectFileDirectory(projectPath)
      }
    val bridgeStep = moduleBuilder.getCustomOptionsStep(wizardContext, disposer) as NewProjectWizardStep
    // Init UI method to
    val panelRef = bridgeStep.javaClass.getDeclaredField("panel")
    panelRef.isAccessible = true
    val panel = panelRef.get(bridgeStep) as NewProjectWizardStepPanel
    panel.component
    //bridgeStep.archetypeMavenData?.archetypeItem = MavenArchetypeItem("org.apache.maven.archetypes", "maven-archetype-webapp")
    //bridgeStep.archetypeMavenData?.archetypeVersion = "1"
    val modulesConfigurator = ModulesConfigurator(context.project, ProjectStructureConfigurable.getInstance(project))
    withContext(Dispatchers.EDT) {
      val createdModules = moduleBuilder.commit(context.project, modulesConfigurator.moduleModel, modulesConfigurator)

      ApplicationManager.getApplication().runWriteAction {
        createdModules?.forEach {
          if (it != null && modulesConfigurator.getModule(it.getName()) != null) {
            modulesConfigurator.getOrCreateModuleEditor(it)
          }
        }
      }
      Disposer.dispose(disposer)
    }
  }

  override fun getName(): String {
    return NAME
  }
}