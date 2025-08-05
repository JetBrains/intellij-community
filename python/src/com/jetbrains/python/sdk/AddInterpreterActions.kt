// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AddInterpreterActions")

package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetCustomToolWizardStep
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentWizard
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsActions
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.allowCreationTargetOfThisType
import com.jetbrains.python.sdk.ModuleOrProject.ModuleAndProject
import com.jetbrains.python.sdk.ModuleOrProject.ProjectOnly
import com.jetbrains.python.sdk.add.collector.PythonNewInterpreterAddedCollector
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterDialog
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterPresenter
import com.jetbrains.python.target.PythonLanguageRuntimeType
import com.jetbrains.python.util.ShowingMessageErrorSync
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.Icon

@ApiStatus.Internal
abstract class DialogAction(
  dynamicText: Supplier<@NlsActions.ActionText String>,
  val icon: Icon,
  val target: @Nls String,
) : AnAction(dynamicText, icon) {
  abstract fun createDialog(): DialogWrapper?
}

@ApiStatus.Internal
fun collectAddInterpreterActions(moduleOrProject: ModuleOrProject, onSdkCreated: Consumer<Sdk>): List<DialogAction> {
  // If module resides on this target, we can't use any target except same target and target types that explicitly allow that
  // example: on ``\\wsl$`` you can only use wsl target and dockers
  val targetModuleSitsOn = when (moduleOrProject) {
    is ModuleAndProject -> PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(moduleOrProject.module)
    is ProjectOnly -> null
  }
  return mutableListOf<DialogAction>().apply {
    if (targetModuleSitsOn == null) {
      add(AddLocalInterpreterAction(moduleOrProject, onSdkCreated::accept))
    }
    addAll(collectNewInterpreterOnTargetActions(moduleOrProject.project, targetModuleSitsOn, onSdkCreated::accept))
  }
}

private fun collectNewInterpreterOnTargetActions(
  project: Project,
  targetTypeModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
  onSdkCreated: Consumer<Sdk>,
): List<DialogAction> =
  PythonInterpreterTargetEnvironmentFactory.EP_NAME.extensionList
    .filter { it.getTargetType().isSystemCompatible() }
    .filter { targetTypeModuleSitsOn == null || targetTypeModuleSitsOn.allowCreationTargetOfThisType(it.getTargetType()) }
    // filter create new interpreter actions on targets that need to be associated with module like PyDockerComposeTargetEnvironmentFactory
    .filterNot { project.isDefault && it.needAssociateWithModule() }
    .map { AddInterpreterOnTargetAction(project, it.getTargetType(), onSdkCreated) }

private class AddLocalInterpreterAction(
  private val moduleOrProject: ModuleOrProject,
  private val onSdkCreated: Consumer<Sdk>,
) : DialogAction(
  dynamicText = PyBundle.messagePointer("python.sdk.action.add.local.interpreter.text"),
  icon = AllIcons.Nodes.HomeFolder,
  target = PyBundle.message("sdk.create.targets.local"),
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    runInEdt {
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    createDialog().show()
  }

  override fun createDialog(): PythonAddLocalInterpreterDialog {
    val dialogPresenter = PythonAddLocalInterpreterPresenter(moduleOrProject, errorSink = ShowingMessageErrorSync).apply {
      // Model provides flow, but we need to call Consumer
      sdkCreatedFlow.oneShotConsumer(onSdkCreated)
    }
    return PythonAddLocalInterpreterDialog(dialogPresenter)
  }
}

@ApiStatus.Internal
fun addLocalInterpreter(moduleOrProject: ModuleOrProject, onSdkCreated: Consumer<Sdk>) {
  AddLocalInterpreterAction(moduleOrProject, onSdkCreated).createDialog().show()
}

private class AddInterpreterOnTargetAction(
  private val project: Project,
  private val targetType: TargetEnvironmentType<*>,
  private val onSdkCreated: Consumer<Sdk>,
) : DialogAction(
  dynamicText = PyBundle.messagePointer("python.sdk.action.add.interpreter.based.on.target.text", targetType.displayName),
  icon = targetType.icon,
  target = targetType.displayName,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val wizard = createDialog()
    if (wizard != null && wizard.showAndGet()) {
      val model = PyConfigurableInterpreterList.getInstance(project).model
      val sdk = (wizard.currentStepObject as? TargetCustomToolWizardStep)?.customTool as? Sdk
      if (sdk != null) {
        PythonNewInterpreterAddedCollector.logPythonNewInterpreterAdded(sdk, isPreviouslyConfigured = true)
        if (model.findSdk(sdk.name) == null) {
          model.addSdk(sdk)
          model.apply()
          onSdkCreated.accept(sdk)
        }
      }
    }
  }

  override fun createDialog(): TargetEnvironmentWizard? {
    return TargetEnvironmentWizard.createWizard(project, targetType, PythonLanguageRuntimeType.getInstance())
  }
}

@ApiStatus.Internal

fun switchToSdk(module: Module, sdk: Sdk, currentSdk: Sdk?) {
  val project = module.project
  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)

  removeTransferredRootsFromModulesWithInheritedSdk(project, currentSdk)
  project.pythonSdk = sdk
  transferRootsToModulesWithInheritedSdk(project, sdk)

  removeTransferredRoots(module, currentSdk)
  module.pythonSdk = sdk
  transferRoots(module, sdk)

  module.excludeInnerVirtualEnv(sdk)
}