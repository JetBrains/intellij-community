// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AddInterpreterActions")

package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetCustomToolWizardStep
import com.intellij.execution.target.TargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentWizard
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.python.pyproject.model.api.ModuleCreateInfo
import com.intellij.python.pyproject.model.api.getModuleInfo
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.allowCreationTargetOfThisType
import com.jetbrains.python.sdk.ModuleOrProject.ModuleAndProject
import com.jetbrains.python.sdk.ModuleOrProject.ProjectOnly
import com.jetbrains.python.sdk.add.collector.PythonNewInterpreterAddedCollector
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterDialog
import com.jetbrains.python.sdk.add.v2.PythonAddLocalInterpreterPresenter
import com.jetbrains.python.sdk.configuration.CreateSdkInfoWithTool
import com.jetbrains.python.target.PythonLanguageRuntimeType
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
      add(createAddLocalInterpreterAction(moduleOrProject, onSdkCreated::accept))
    }
    addAll(collectNewInterpreterOnTargetActions(moduleOrProject.project, targetModuleSitsOn, onSdkCreated::accept))
  }
}

private fun collectNewInterpreterOnTargetActions(
  project: Project,
  targetTypeModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
  onSdkCreated: (Sdk) -> Unit,
): List<DialogAction> =
  PythonInterpreterTargetEnvironmentFactory.EP_NAME.extensionList
    .filter { it.getTargetType().isSystemCompatible() }
    .filter { targetTypeModuleSitsOn == null || targetTypeModuleSitsOn.allowCreationTargetOfThisType(it.getTargetType()) }
    // filter create new interpreter actions on targets that need to be associated with module like PyDockerComposeTargetEnvironmentFactory
    .filterNot { project.isDefault && it.needAssociateWithModule() }
    .map { AddInterpreterOnTargetAction(project, it.getTargetType(), onSdkCreated) }

internal class AddLocalInterpreterAction(
  private val moduleOrProject: ModuleOrProject,
  private val onSdkCreated: (Sdk) -> Unit,
  private val bestGuessCreateSdkInfo: Deferred<CreateSdkInfoWithTool?>,
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
    val dialogPresenter = PythonAddLocalInterpreterPresenter(
      moduleOrProject, errorSink = ShowingMessageErrorSync, bestGuessCreateSdkInfo = bestGuessCreateSdkInfo
    ).apply {
      // Model provides flow, but we need to call Consumer
      sdkCreatedFlow.oneShotConsumer(onSdkCreated)
    }
    return PythonAddLocalInterpreterDialog(dialogPresenter)
  }
}

@ApiStatus.Internal
fun addLocalInterpreter(moduleOrProject: ModuleOrProject, onSdkCreated: (Sdk) -> Unit): Unit =
  createAddLocalInterpreterAction(moduleOrProject, onSdkCreated).createDialog().show()

internal class AddInterpreterOnTargetAction(
  private val project: Project,
  private val targetType: TargetEnvironmentType<*>,
  private val onSdkCreated: (Sdk) -> Unit,
) : DialogAction(
  dynamicText = PyBundle.messagePointer("python.sdk.action.add.interpreter.based.on.target.text", targetType.displayName),
  icon = targetType.icon,
  target = targetType.displayName,
), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    createDialog()?.show()
  }

  override fun createDialog(): TargetEnvironmentWizard? {
    val wizard = TargetEnvironmentWizard.createWizard(project, targetType, PythonLanguageRuntimeType.Helper.getInstance())

    wizard?.let {
      Disposer.register(it.disposable, Disposable {
        exitHandler(it)
      })
    }

    return wizard
  }

  private fun exitHandler(dialogWrapper: TargetEnvironmentWizard) {
    if (dialogWrapper.exitCode != OK_EXIT_CODE) return
    val sdk = (dialogWrapper.currentStepObject as? TargetCustomToolWizardStep)?.customTool as? Sdk ?: return

    service<LogCollectorService>().coroutineScope.launch(Dispatchers.Default) {
      PythonNewInterpreterAddedCollector.logPythonNewInterpreterAdded(sdk, isPreviouslyConfigured = true)
    }
    onSdkCreated(sdk)
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

@Service
private class LogCollectorService(val coroutineScope: CoroutineScope)

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
private class ToolDetectionService(project: Project, val coroutineScope: CoroutineScope) {
  private val cacheManager = CachedValuesManager.getManager(project)

  private fun tryDetectTool(module: Module): Deferred<CreateSdkInfoWithTool?> = cache(module)

  private fun cache(module: Module): Deferred<CreateSdkInfoWithTool?> = cacheManager.getParameterizedCachedValue(
    module,
    CACHE_KEY,
    ::detectBestToolAsync,
    false,
    module
  )

  private suspend fun detectBestToolForModule(module: Module): CreateSdkInfoWithTool? =
    when (val i = module.getModuleInfo()) {
      is ModuleCreateInfo.CreateSdkInfoWrapper -> CreateSdkInfoWithTool(i.createSdkInfo, i.toolId)
      is ModuleCreateInfo.SameAs -> detectBestToolForModule(i.parentModule)
      null -> null
    }

  private fun detectBestToolAsync(module: Module): CachedValueProvider.Result<Deferred<CreateSdkInfoWithTool?>> {
    val result = coroutineScope.async {
      withContext(TraceContext(PyBundle.message("trace.context.python.tool.detection.service.detect.tools.for.module", module.name))) {
        detectBestToolForModule(module)
      }
    }
    result.invokeOnCompletion { getOrCreateModificationTracker(module).incModificationCount() }
    return CachedValueProvider.Result.create(result, getOrCreateModificationTracker(module))
  }

  private fun getOrCreateModificationTracker(module: Module): SimpleModificationTracker {
    val existing = module.getUserData(MODIFICATION_TRACKER_KEY)
    if (existing != null) return existing

    return synchronized(this) {
      module.getOrCreateUserDataUnsafe(MODIFICATION_TRACKER_KEY) { SimpleModificationTracker() }
    }
  }

  companion object {
    fun forModule(module: Module): Deferred<CreateSdkInfoWithTool?> = module.project.service<ToolDetectionService>().tryDetectTool(module)

    private val MODIFICATION_TRACKER_KEY = Key.create<SimpleModificationTracker>("PyAddInterpreterModificationTracker")
    private val CACHE_KEY = Key.create<ParameterizedCachedValue<Deferred<CreateSdkInfoWithTool?>, Module>>("PyAddInterpreterCache")
  }
}

private fun createAddLocalInterpreterAction(moduleOrProject: ModuleOrProject, onSdkCreated: (Sdk) -> Unit): AddLocalInterpreterAction {
  val bestGuessCreateSdkInfo = moduleOrProject.moduleIfExists?.let {
    ToolDetectionService.forModule(it)
  } ?: CompletableDeferred(value = null)
  return AddLocalInterpreterAction(moduleOrProject, onSdkCreated, bestGuessCreateSdkInfo)
}
