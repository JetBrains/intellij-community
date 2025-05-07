// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.python.featuresTrainer.ift

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.FormBuilder
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.Result
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.newProject.steps.ProjectSpecificSettingsStep
import com.jetbrains.python.projectCreation.createVenvAndSdk
import com.jetbrains.python.sdk.PySdkToInstall
import com.jetbrains.python.sdk.add.PySdkPathChoosingComboBox
import com.jetbrains.python.sdk.add.addBaseInterpretersAsync
import com.jetbrains.python.sdk.findBaseSdks
import com.jetbrains.python.sdk.pythonSdk
import com.jetbrains.python.statistics.modules
import com.jetbrains.python.util.ShowingMessageErrorSync
import training.dsl.LessonContext
import training.lang.AbstractLangSupport
import training.learn.CourseManager
import training.learn.course.KLesson
import training.learn.exceptons.NoSdkException
import training.project.ProjectUtils
import training.project.ReadMeCreator
import training.statistic.LearningInternalProblems
import training.statistic.LessonStartingWay
import training.ui.LearningUiManager
import training.util.getFeedbackLink
import training.util.isLearningProject
import java.awt.Dimension
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.math.max

internal class PythonLangSupport(private val errorSink: ErrorSink = ShowingMessageErrorSync) : AbstractLangSupport() {

  override val contentRootDirectoryName = "PyCharmLearningProject"

  override val primaryLanguage = "Python"

  override val defaultProductName: String = "PyCharm"

  private val sourcesDirectoryName: String = "src"

  override val scratchFileName: String = "Learning.py"

  override val langCourseFeedback get() = getFeedbackLink(this, false)

  override fun applyToProjectAfterConfigure(): (Project) -> Unit = { project ->
    ProjectUtils.markDirectoryAsSourcesRoot(project, sourcesDirectoryName)
  }

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean = true
  override val readMeCreator = ReadMeCreator()

  override fun installAndOpenLearningProject(
    contentRoot: Path,
    projectToClose: Project?,
    postInitCallback: (learnProject: Project) -> Unit,
  ) {
    // if we open project with isProjectCreatedFromWizard flag as true, PythonSdkConfigurator will not run and configure our sdks,
    // and we will configure it individually without any race conditions
    val openProjectTask = OpenProjectTask {
      this.projectToClose = projectToClose
      isProjectCreatedWithWizard = true
    }
    ProjectUtils.simpleInstallAndOpenLearningProject(contentRoot, this, openProjectTask, postInitCallback)
  }

  @Throws(NoSdkException::class)
  @RequiresEdt
  override fun getSdkForProject(project: Project, selectedSdk: Sdk?): Sdk = runWithModalProgressBlocking(project, "...") {
    when (val r = createVenvAndSdk(project)) {
      is Result.Failure -> {
        errorSink.emit(r.error)
        null
      }
      is Result.Success -> r.result
    } ?: throw NoSdkException()
  }


  @RequiresEdt
  override fun applyProjectSdk(sdk: Sdk, project: Project) {
  }


  override fun checkSdk(sdk: Sdk?, project: Project) {
  }

  override val sampleFilePath = "src/sandbox.py"

  override fun startFromWelcomeFrame(startCallback: (Sdk?) -> Unit) {
    val allExistingSdks = listOf(*PyConfigurableInterpreterList.getInstance(null).model.sdks)
    val existingSdks = ProjectSpecificSettingsStep.getValidPythonSdks(allExistingSdks)

    ApplicationManager.getApplication().executeOnPooledThread {
      val context = UserDataHolderBase()
      val baseSdks = findBaseSdks(existingSdks, null, context)

      invokeLater {
        if (baseSdks.isEmpty()) {
          val sdk = showSdkChoosingDialog(existingSdks, context)
          if (sdk != null) {
            startCallback(sdk)
          }
        }
        else startCallback(null)
      }
    }
  }

  private fun showSdkChoosingDialog(existingSdks: List<Sdk>, context: UserDataHolder): Sdk? {
    val baseSdkField = PySdkPathChoosingComboBox()

    val warningPlaceholder = JLabel()
    val formPanel = FormBuilder.createFormBuilder()
      .addComponent(warningPlaceholder)
      .addLabeledComponent(PySdkBundle.message("python.venv.base.label"), baseSdkField)
      .panel

    formPanel.preferredSize = Dimension(max(formPanel.preferredSize.width, 500), formPanel.preferredSize.height)
    val dialog = object : DialogWrapper(ProjectManager.getInstance().defaultProject) {
      override fun createCenterPanel(): JComponent = formPanel

      init {
        title = PyBundle.message("sdk.select.path")
        init()
      }
    }

    addBaseInterpretersAsync(baseSdkField, existingSdks, null, context) {
      val selectedSdk = baseSdkField.selectedSdk
      if (selectedSdk is PySdkToInstall) {
        val installationWarning = selectedSdk.getInstallationWarning(Messages.getOkButton())
        warningPlaceholder.text = "<html>$installationWarning</html>"
      }
      else {
        warningPlaceholder.text = ""
      }
    }

    dialog.title = PythonLessonsBundle.message("choose.python.sdk.to.start.learning.header")
    return if (dialog.showAndGet()) {
      baseSdkField.selectedSdk
    }
    else null
  }

  override fun isSdkConfigured(project: Project): Boolean = project.pythonSdk != null

  override val sdkConfigurationTasks: LessonContext.(lesson: KLesson) -> Unit = { lesson ->
    task {
      stateCheck {
        isSdkConfigured(project)
      }
      val configureCallbackId = LearningUiManager.addCallback {
        val module = project.modules.singleOrNull()
        PyInterpreterInspection.InterpreterSettingsQuickFix.showPythonInterpreterSettings(project, module)
      }
      if (useUserProjects || isLearningProject(project, primaryLanguage)) {
        showWarning(PythonLessonsBundle.message("no.interpreter.in.learning.project", configureCallbackId),
                    problem = LearningInternalProblems.NO_SDK_CONFIGURED) {
          !isSdkConfigured(project)
        }
      }
      else {
        // for Scratch lessons in the non-learning project
        val openCallbackId = LearningUiManager.addCallback {
          CourseManager.instance.openLesson(project, lesson, LessonStartingWay.NO_SDK_RESTART,
                                            forceStartLesson = true,
                                            forceOpenLearningProject = true)
        }
        showWarning(PythonLessonsBundle.message("no.interpreter.in.user.project", openCallbackId, configureCallbackId)) {
          !isSdkConfigured(project)
        }
      }
    }
  }

  @RequiresReadLock
  override fun getProtectedDirs(project: Project): Set<Path> {
    return project.getSdks().mapNotNull { it.homePath }.map { Path.of(it) }.toSet()
  }
}

@RequiresReadLock
private fun Project.getSdks(): Set<Sdk> {
  val projectSdk = ProjectRootManager.getInstance(this).projectSdk
  val moduleSdks = modules.mapNotNull {
    ModuleRootManager.getInstance(it).sdk
  }
  return (moduleSdks + (projectSdk?.let { listOf(it) } ?: emptyList())).toSet()
}
