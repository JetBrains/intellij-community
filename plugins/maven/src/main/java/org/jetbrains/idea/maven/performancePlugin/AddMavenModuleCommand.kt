// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.application.EDT
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeItem
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizard
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizardData.Companion.archetypeMavenData

/**
 * The command adds new maven module with/without archetype and parent
 * Syntax: %addMavenModule module name|parent module name|archetypeGroupId|archetypeArtifactId|archetypeVersion
 * Example: %addMavenModule moduleWithParent|parentModule|org.apache.maven.archetypes|maven-archetype-archetype|1.0 -
 * will create moduleWithParent module from archetype org.apache.maven.archetypes:maven-archetype-archetype:1.0 with parent parentModule
 * Example: %addMavenModule moduleWithoutParent||org.apache.maven.archetypes|maven-archetype-archetype|1.0 -
 * will create moduleWithoutParent module from archetype org.apache.maven.archetypes:maven-archetype-archetype:1.0 without parent
 * Example: %addMavenModule moduleWithoutParent|||| - will create moduleWithoutParent module without archetype and parent
 * Example: %addMavenModule moduleWitParent|parentModule||| - will create moduleWithoutParent module without archetype and with parent parentModule
 */
class AddMavenModuleCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "addMavenModule"
    const val PREFIX = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val disposable = Disposer.newDisposable()
    try {
      val project = context.project
      val (projectName,
        parentModuleName,
        archetypeGroupId,
        archetypeArtifactId,
        archetypeVersion) = extractCommandArgument(PREFIX).split("|")
      val parentModule = ModuleManager.getInstance(project).modules.firstOrNull { it.name == parentModuleName }
      val projectPath = parentModule?.moduleNioFile?.parent?.toString() ?: project.basePath!!
      val projectStructureConfigurable = ProjectStructureConfigurable.getInstance(project)
      val modulesConfigurator = ModulesConfigurator(project, projectStructureConfigurable)
      val withArchetype = archetypeGroupId.isNotEmpty()
      val moduleBuilder = if (withArchetype)
        ModuleBuilder.getAllBuilders().first { it::class == MavenArchetypeNewProjectWizard.Builder::class }
      else
        LanguageGeneratorNewProjectWizard.EP_NAME.getIterable().first().let {
          val adapter = BaseLanguageGeneratorNewProjectWizard(WizardContext(project, disposable), it!!)
          TemplatesGroup(object : GeneratorNewProjectWizardBuilderAdapter(adapter) {}).moduleBuilder
        }


      val bridgeStep = moduleBuilder!!.getCustomOptionsStep(WizardContext(project, disposable)
                                                              .apply { setProjectFileDirectory(projectPath) }, disposable)
      // Call setupUI method for init lateinit vars
      bridgeStep?.validate()

      (bridgeStep as NewProjectWizardStep).apply {
        baseData?.name = projectName
        baseData?.path = projectPath
        if (withArchetype) {
          archetypeMavenData?.parentData = parentModule?.let { MavenProjectsManager.getInstance(project).findProject(it) }
          archetypeMavenData?.archetypeItem = MavenArchetypeItem(archetypeGroupId, archetypeArtifactId)
          archetypeMavenData?.archetypeVersion = archetypeVersion
        }
        else {
          javaMavenData?.parentData = parentModule?.let { MavenProjectsManager.getInstance(project).findProject(it) }
          javaBuildSystemData?.buildSystem = "Maven"
        }
      }

      withContext(Dispatchers.EDT) {
        moduleBuilder.commit(project, modulesConfigurator.moduleModel, modulesConfigurator)
      }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  override fun getName(): String {
    return NAME
  }
}