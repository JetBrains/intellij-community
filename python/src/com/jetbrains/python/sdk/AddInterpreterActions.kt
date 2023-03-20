// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("AddInterpreterActions")

package com.jetbrains.python.sdk

import com.intellij.execution.target.*
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.allowCreationTargetOfThisType
import com.jetbrains.python.sdk.add.PyAddSdkDialog
import com.jetbrains.python.sdk.add.target.PyAddTargetBasedSdkDialog
import com.jetbrains.python.target.PythonLanguageRuntimeType
import java.util.function.Consumer

fun collectAddInterpreterActions(project: Project, module: Module?, onSdkCreated: Consumer<Sdk>): List<AnAction> {
  // If module resides on this target, we can't use any target except same target and target types that explicitly allow that
  // example: on ``\\wsl$`` you can only use wsl target and dockers
  val targetModuleSitsOn = module?.let {
    PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(it)
  }
  return mutableListOf<AnAction>().apply {
    if (targetModuleSitsOn == null) {
      add(AddLocalInterpreterAction(project, module, onSdkCreated::accept))
    }
    addAll(collectNewInterpreterOnTargetActions(project, targetModuleSitsOn, onSdkCreated::accept))
  }
}

private fun collectNewInterpreterOnTargetActions(project: Project,
                                                 targetTypeModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
                                                 onSdkCreated: Consumer<Sdk>): List<AnAction> =
  PythonInterpreterTargetEnvironmentFactory.EP_NAME.extensionList
    .filter { it.getTargetType().isSystemCompatible() }
    .filter { targetTypeModuleSitsOn == null || targetTypeModuleSitsOn.allowCreationTargetOfThisType(it.getTargetType()) }
    // filter create new interpreter actions on targets that need to be associated with module like PyDockerComposeTargetEnvironmentFactory
    .filterNot { project.isDefault && it.needAssociateWithModule() }
    .map { AddInterpreterOnTargetAction(project, it.getTargetType(), onSdkCreated) }

private class AddLocalInterpreterAction(private val project: Project,
                                        private val module: Module?,
                                        private val onSdkCreated: Consumer<Sdk>)
  : AnAction(PyBundle.messagePointer("python.sdk.action.add.local.interpreter.text"), AllIcons.Nodes.HomeFolder), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val model = PyConfigurableInterpreterList.getInstance(project).model

    PyAddTargetBasedSdkDialog.show(
      project,
      module,
      model.sdks.asList(),
      Consumer {
        if (it != null && model.findSdk(it.name) == null) {
          model.addSdk(it)
          model.apply()
          onSdkCreated.accept(it)
        }
      }
    )
  }
}

private class AddInterpreterOnTargetAction(private val project: Project,
                                           private val targetType: TargetEnvironmentType<*>,
                                           private val onSdkCreated: Consumer<Sdk>)
  : AnAction(PyBundle.messagePointer("python.sdk.action.add.interpreter.based.on.target.text", targetType.displayName), targetType.icon),
    DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val wizard = TargetEnvironmentWizard.createWizard(project, targetType, PythonLanguageRuntimeType.getInstance())
    if (wizard != null && wizard.showAndGet()) {
      val model = PyConfigurableInterpreterList.getInstance(project).model
      val sdk = (wizard.currentStepObject as? TargetCustomToolWizardStep)?.customTool as? Sdk
      if (sdk != null && model.findSdk(sdk.name) == null) {
        model.addSdk(sdk)


        model.apply()
        onSdkCreated.accept(sdk)
      }
    }
  }
}

class AddInterpreterAction(val project: Project, val module: Module, private val currentSdk: Sdk?)
  : DumbAwareAction(PyBundle.messagePointer("python.sdk.popup.add.interpreter")) {

  override fun actionPerformed(e: AnActionEvent) {
    val model = PyConfigurableInterpreterList.getInstance(project).model

    PyAddSdkDialog.show(
      project,
      module,
      model.sdks.asList(),
      Consumer {
        if (it != null && model.findSdk(it.name) == null) {
          model.addSdk(it)
          model.apply()
          switchToSdk(module, it, currentSdk)
        }
      }
    )
  }
}

fun switchToSdk(module: Module, sdk: Sdk, currentSdk: Sdk?) {
  val project = module.project
  (sdk.sdkType as PythonSdkType).setupSdkPaths(sdk)

  removeTransferredRootsFromModulesWithInheritedSdk(project, currentSdk)
  project.pythonSdk = sdk
  transferRootsToModulesWithInheritedSdk(project, sdk)

  removeTransferredRoots(module, currentSdk)
  module.pythonSdk = sdk
  transferRoots(module, sdk)
}