// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.performancePlugin

import com.intellij.execution.ExecutionManager
import com.intellij.ide.GeneralSettings
import com.intellij.ide.impl.NewProjectUtil
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.TrustedPaths
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData.Companion.javaBuildSystemData
import com.intellij.ide.util.newProjectWizard.TemplatesGroup
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.language.BaseLanguageGeneratorNewProjectWizard
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.util.Disposer
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import com.jetbrains.performancePlugin.commands.SetupProjectSdkUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.await
import org.jetbrains.idea.maven.performancePlugin.dto.NewMavenProjectDto
import org.jetbrains.idea.maven.performancePlugin.utils.MavenCommandsExecutionListener
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.wizards.MavenJavaNewProjectWizardData.Companion.javaMavenData
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeItem
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizard
import org.jetbrains.idea.maven.wizards.archetype.MavenArchetypeNewProjectWizardData.Companion.archetypeMavenData
import java.nio.file.Path

/**
 * The command creates a new maven project with/without a maven archetype.
 * The project also could be added as module (linked to the current project)
 * Argument is serialized [NewMavenProjectDto] as json
 */
class CreateMavenProjectCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
  companion object {
    const val NAME = "createMavenProject"
    const val PREFIX = "$CMD_PREFIX$NAME"

    suspend fun getNewProject(builder: ModuleBuilder, projectName: String, projectPath: Path, wizardContext: WizardContext): Project {
      return builder.createProject(projectName, projectPath.toString())!!.apply {
        save()
        writeIntentReadAction {
          NewProjectUtil.setCompilerOutputPath(this, projectPath.resolve("out").toString())
        }
        wizardContext.projectJdk?.also { jdk ->
          blockingContext {
            ApplicationManager.getApplication().runWriteAction {
              JavaSdkUtil.applyJdkToProject(this, jdk)
            }
          }
        }
      }
    }

    suspend fun runNewProject(projectPath: Path, newProject: Project, oldProject: Project, context: PlaybackContext) {
      ProjectUtil.updateLastProjectLocation(projectPath)
      val fileName = projectPath.fileName
      val options = OpenProjectTask
        .build()
        .withProject(newProject)
        .withProjectName(fileName.toString())
        .withProjectToClose(oldProject)
      TrustedPaths.getInstance().setProjectPathTrusted(projectPath, true)
      GeneralSettings.getInstance().confirmOpenNewProject = GeneralSettings.OPEN_PROJECT_SAME_WINDOW

      ProjectManagerEx.getInstanceEx().openProjectAsync(projectStoreBaseDir = projectPath, options = options)
      context.setProject(newProject)
    }
  }


  override suspend fun doExecute(context: PlaybackContext) {
    val disposable = Disposer.newDisposable()

    try {
      val project = context.project
      val newMavenProjectDto = deserializeOptionsFromJson(extractCommandArgument(PREFIX), NewMavenProjectDto::class.java)
      val parentModule = ModuleManager.getInstance(project).modules.firstOrNull { it.name == newMavenProjectDto.parentModuleName }
      val projectPath = (parentModule?.moduleNioFile?.parent ?: Path.of(project.basePath!!)).resolve(newMavenProjectDto.projectName)
      val modulesConfigurator = if (newMavenProjectDto.asModule)
        ModulesConfigurator(project, ProjectStructureConfigurable.getInstance(project))
      else
        null
      val withArchetype = newMavenProjectDto.mavenArchetypeInfo != null

      val wizardContext = WizardContext(if (newMavenProjectDto.asModule) project else null, disposable).apply {
        projectJdk = newMavenProjectDto.sdkObject?.let {
          @Suppress("TestOnlyProblems")
          SetupProjectSdkUtil.setupOrDetectSdk(it.sdkName, it.sdkType, it.sdkPath.toString())
        } ?: ProjectRootManager.getInstance(project).projectSdk
      }

      val moduleBuilder = if (withArchetype)
        ModuleBuilder.getAllBuilders().first { it::class == MavenArchetypeNewProjectWizard.Builder::class }
      else
        LanguageGeneratorNewProjectWizard.EP_NAME.getIterable().first().let {
          val adapter = BaseLanguageGeneratorNewProjectWizard(wizardContext, it!!)
          TemplatesGroup(object : GeneratorNewProjectWizardBuilderAdapter(adapter) {}).moduleBuilder
        }
      withContext(Dispatchers.EDT) {
        val newProject = if (newMavenProjectDto.asModule) project
        else getNewProject(moduleBuilder!!, newMavenProjectDto.projectName, projectPath, wizardContext)

        val bridgeStep = moduleBuilder!!.getCustomOptionsStep(wizardContext, disposable)
        // Call setupUI method for init lateinit vars
        bridgeStep?.validate()

        (bridgeStep as NewProjectWizardStep).apply {
          baseData?.name = newMavenProjectDto.projectName
          baseData?.path = projectPath.parent.toString()
          if (withArchetype) {
            archetypeMavenData?.apply {
              parentData = parentModule?.let { MavenProjectsManager.getInstance(project).findProject(it) }
              archetypeItem = MavenArchetypeItem(newMavenProjectDto.mavenArchetypeInfo!!.groupId, newMavenProjectDto.mavenArchetypeInfo.artefactId)
              archetypeVersion = newMavenProjectDto.mavenArchetypeInfo.version
            }
          }
          else {
            javaMavenData?.parentData = parentModule?.let { MavenProjectsManager.getInstance(project).findProject(it) }
            javaBuildSystemData?.buildSystem = "Maven"
          }
        }
        val promise = AsyncPromise<Any?>()
        if (withArchetype) {
          newProject.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC, MavenCommandsExecutionListener(promise))
        }
        else {
          promise.setResult(null)
        }

        moduleBuilder.commit(newProject, modulesConfigurator?.moduleModel, modulesConfigurator ?: ModulesProvider.EMPTY_MODULES_PROVIDER)

        if (!newMavenProjectDto.asModule) runNewProject(projectPath, newProject, project, context)
        promise.await()
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