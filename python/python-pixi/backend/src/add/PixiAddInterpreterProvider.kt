// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.add

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.execService.ZeroCodeStdoutTransformer
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pixi.backend.PyPixiBackendBundle
import com.intellij.python.pixi.backend.PIXI_TOOL_ID
import com.intellij.python.pixi.backend.sdk.createPixiSdkData
import com.intellij.python.pixi.backend.evolution.createPixiNamedEnvironment
import com.intellij.python.pixi.backend.evolution.pixiReservedEnvironmentNames
import com.intellij.python.pixi.backend.workspace.DEFAULT_ENVIRONMENT
import com.intellij.python.pixi.backend.workspace.PixiWorkspace
import com.intellij.python.pixi.backend.workspace.findPixiWorkspace
import com.intellij.python.pytools.backend.Version
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.performToolInstallation
import com.intellij.python.pixi.backend.workspace.createPixiRuntime
import com.intellij.python.pixi.backend.workspace.resolvePixiWorkspacePath
import com.intellij.python.pixi.backend.workspace.pixiFileExists
import com.intellij.python.pixi.backend.workspace.PixiWorkspaceOperations
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.common.PyEnvRef
import com.intellij.python.sdk.common.PyInterpreterRef
import com.jetbrains.python.psi.LanguageLevel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.components.ActionLink
import com.intellij.util.asDisposable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.findPyProject
import com.jetbrains.python.sdk.add.v2.EelOrTarget
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterProvider
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterProviderContext
import com.jetbrains.python.sdk.add.v2.PythonEnvironmentManager
import com.jetbrains.python.sdk.add.v2.PythonExistingEnvironmentConfigurator
import com.jetbrains.python.sdk.add.v2.PythonInterpreterCreationTargets
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.ValidatedPathField
import com.jetbrains.python.sdk.add.v2.customEnvironmentStatistics
import com.jetbrains.python.sdk.add.v2.pathHolder
import com.jetbrains.python.sdk.add.v2.validatablePathField
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import javax.swing.Icon

internal class PixiAddInterpreterProvider : PythonAddInterpreterProvider {
  override val manager: PythonEnvironmentManager = PixiEnvironmentManager
  override val quickActionName: String = PyPixiBackendBundle.message("python.pixi.add.quick.action")

  override fun <P : PathHolder> createNewEnvironment(
    context: PythonAddInterpreterProviderContext<P>,
  ): PythonNewEnvironmentCreator<P> = PixiEnvironmentCreator(context.model, context.errorSink)

  override fun <P : PathHolder> selectExistingEnvironment(
    context: PythonAddInterpreterProviderContext<P>,
  ): PythonExistingEnvironmentConfigurator<P> = PixiExistingEnvironmentSelector(context.model, context.errorSink)
}

private object PixiEnvironmentManager : PythonEnvironmentManager {
  override val toolId: ToolId = PIXI_TOOL_ID
  override val displayName: String = PyPixiBackendBundle.message("python.pixi.add.manager.name")
  override val icon: Icon = AllIcons.Nodes.Package
  override val order: Int = 6
  override val sshAutoUploadRequired: Boolean = true

  override fun supports(fileSystem: com.jetbrains.python.sdk.add.v2.FileSystem<*>): Boolean = true
}

private class PixiEnvironmentCreator<P : PathHolder>(
  override val model: PythonMutableTargetAddInterpreterModel<P>,
  errorSink: ErrorSink,
) : PythonNewEnvironmentCreator<P>(model) {
  private val form = PixiEnvironmentForm(
    model,
    existingOnly = false,
    canInitializeWorkspace = true,
    errorSink = errorSink,
  )

  override val toolExecutable get() = form.toolExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { model.fileSystem.persistCustomToolPath(it, PixiPyTool.getInstance()) }
  override val persistToolExecutableOnSetup: Boolean get() = form.isToolPathUserEdited

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) = form.setupUI(panel, validationRequestor)

  override fun onShown(scope: CoroutineScope) = form.onShown(scope)

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<PythonInterpreter> {
    val selection = form.selection().getOr { return it }
    if (selection.initializeWorkspace) {
      initializePixiWorkspace(model.fileSystem, selection).getOr { return it }
    }
    val runtime = createPixiRuntime(model.fileSystem, selection.executable, selection.workspace.root)
    PixiWorkspaceOperations.run(selection.workspace.root) {
      if (selection.createEnvironment) {
        createPixiNamedEnvironment(runtime, selection.workspace.root, selection.environment, checkNotNull(selection.pythonVersion))
          .getOr { return@run it }
      }
      runtime.executeAndHandleErrors(
        "install", "--environment=${selection.environment}",
        transformer = ZeroCodeStdoutTransformer,
      )
    }.getOr { return it }
    return form.createSdk(moduleOrProject, selection)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets) =
    customEnvironmentStatistics(InterpreterType.PIXI, target, previouslyConfigured = false)
}

private class PixiExistingEnvironmentSelector<P : PathHolder>(
  override val model: PythonMutableTargetAddInterpreterModel<P>,
  errorSink: ErrorSink,
) : PythonExistingEnvironmentConfigurator<P>(model) {
  private val form = PixiEnvironmentForm(model, existingOnly = true, errorSink = errorSink)

  override val toolExecutable get() = form.toolExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { model.fileSystem.persistCustomToolPath(it, PixiPyTool.getInstance()) }
  override val persistToolExecutableOnSetup: Boolean get() = form.isToolPathUserEdited

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) = form.setupUI(panel, validationRequestor)

  override fun onShown(scope: CoroutineScope) = form.onShown(scope)

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<PythonInterpreter> {
    val selection = form.selection().getOr { return it }
    return form.createSdk(moduleOrProject, selection)
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets) =
    customEnvironmentStatistics(InterpreterType.PIXI, target, previouslyConfigured = true)
}

internal class PixiEnvironmentForm<P : PathHolder>(
  private val model: PythonMutableTargetAddInterpreterModel<P>,
  private val existingOnly: Boolean,
  private val canInitializeWorkspace: Boolean = false,
  private val tool: PyTool = PixiPyTool.getInstance(),
  private val errorSink: ErrorSink = ErrorSink(),
  private val workspacePathResolver: suspend (P?, Path, String) -> PyResult<P> = { executable, root, relativePath ->
    resolvePixiWorkspacePath(model.fileSystem, executable, root, relativePath)
  },
) {
  val toolExecutable: ObservableMutableProperty<ValidatedPath.Executable<P>?> = model.propertyGraph.property(null)
  private val selectedEnvironment: ObservableMutableProperty<PixiEnvironment?> = model.propertyGraph.property(null)
  private val initializeWorkspace: ObservableMutableProperty<Boolean> = model.propertyGraph.property(false)
  private val createEnvironment: ObservableMutableProperty<Boolean> = model.propertyGraph.property(false)
  private val editEnvironmentName: ObservableMutableProperty<Boolean> = model.propertyGraph.property(false)
  private val selectedPythonVersion: ObservableMutableProperty<String> = model.propertyGraph.property(
    LanguageLevel.getLatest().toPythonVersion(),
  )
  private val initialEnvironmentName: ObservableMutableProperty<String> = model.propertyGraph.property(DEFAULT_ENVIRONMENT)
  private val newWorkspaceRoot: ObservableMutableProperty<Path?> = model.propertyGraph.property(null)
  private var reservedEnvironmentNames: Set<String> = emptySet()
  internal val toolValidator = ToolValidator(model.fileSystem, tool, toolExecutable, model.propertyGraph)
  private lateinit var executableField: ValidatedPathField<Version, P, ValidatedPath.Executable<P>>
  private lateinit var environmentComboBox: ComboBox<PixiEnvironment?>

  val isToolPathUserEdited: Boolean
    get() = this::executableField.isInitialized && executableField.isUserEdited

  @get:TestOnly
  internal val initializesNewWorkspace: Boolean
    get() = initializeWorkspace.get()

  @TestOnly
  internal fun setToolExecutable(executable: P) {
    toolExecutable.set(ValidatedPath.Executable(
      com.jetbrains.python.sdk.add.v2.PathHolderAndValidationResult.Success(Version("test"), executable)
    ))
  }

  fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    executableField = panel.validatablePathField(
      fileSystem = model.fileSystem,
      pathValidator = toolValidator,
      validationRequestor = validationRequestor,
      labelText = PyBundle.message("sdk.create.custom.venv.executable.path", "Pixi"),
      missingExecutableText = PyBundle.message("sdk.create.custom.venv.missing.text", "Pixi"),
      installAction = createInstallFix(),
      canBeEdited = model.fileSystem.toolPathCanBePersisted,
    )
    panel.row(PyPixiBackendBundle.message("python.pixi.add.environment.label")) {
      environmentComboBox = comboBox(emptyList<PixiEnvironment?>(), textListCellRenderer {
        if (it?.createNew == true) PyPixiBackendBundle.message("python.pixi.add.environment.new") else it?.name.orEmpty()
      })
        .bindItem(selectedEnvironment)
        .validationRequestor(validationRequestor)
        .validationOnInput { component ->
          if (component.isVisible && component.selectedItem == null) {
            ValidationInfo(PyPixiBackendBundle.message("python.pixi.add.environment.not.selected"))
          }
          else null
        }
        .align(Align.FILL)
        .visibleIf(initializeWorkspace.not())
        .component
      environmentComboBox.addActionListener {
        createEnvironment.set((environmentComboBox.selectedItem as? PixiEnvironment)?.createNew == true)
        if (createEnvironment.get() && initialEnvironmentName.get() in reservedEnvironmentNames) {
          initialEnvironmentName.set(generateSequence(1) { it + 1 }.map { if (it == 1) "env" else "env-$it" }
                                       .first { it !in reservedEnvironmentNames })
        }
        editEnvironmentName.set(initializeWorkspace.get() || createEnvironment.get())
      }
    }
    panel.row(PyPixiBackendBundle.message("python.pixi.add.environment.name.label")) {
      textField()
        .bindText(initialEnvironmentName)
        .validationRequestor(validationRequestor)
        .validationOnInput { component ->
          if (!component.isVisible) return@validationOnInput null
          val name = component.text.trim()
          when {
            name.isEmpty() -> ValidationInfo(PyPixiBackendBundle.message("python.pixi.add.environment.name.required"))
            !name.matches(ENVIRONMENT_NAME_PATTERN) -> ValidationInfo(PyPixiBackendBundle.message("python.pixi.add.environment.name.invalid"))
            createEnvironment.get() && name in reservedEnvironmentNames ->
              ValidationInfo(PyPixiBackendBundle.message("python.pixi.add.environment.name.taken", name))
            else -> null
          }
        }
        .align(Align.FILL)
    }.visibleIf(editEnvironmentName)
    panel.row(PyPixiBackendBundle.message("python.pixi.add.python.version.label")) {
      comboBox(LanguageLevel.SUPPORTED_LEVELS.filter { it >= LanguageLevel.PYTHON38 }.reversed().map { it.toPythonVersion() })
        .bindItem(selectedPythonVersion)
        .align(Align.FILL)
    }.visibleIf(editEnvironmentName)
  }

  private fun createInstallFix(): ActionLink? {
    val eelDescriptor = when (val environment = model.fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> environment.eel
      is EelOrTarget.IsTarget -> return null
    }
    val title = PyBundle.message("sdk.create.custom.venv.install.fix.title", "Pixi")
    return ActionLink(title) {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), title) {
        tool.performToolInstallation(eelDescriptor.toEelApi()).getOr {
          errorSink.emit(it.error)
          return@runWithModalProgressBlocking
        }
        toolValidator.autodetectExecutable()
      }
    }
  }

  fun onShown(scope: CoroutineScope) {
    val uiScope = scope + Dispatchers.EDT
    val needsExecutable = when (model.fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> false
      is EelOrTarget.IsTarget -> existingOnly
    }
    val executable = MutableStateFlow(toolExecutable.get()?.pathHolder?.successOrNull)
    toolExecutable.afterChange(uiScope.asDisposable()) { executable.value = it?.pathHolder?.successOrNull }
    toolValidator.initialize(uiScope)
    executableField.initialize(uiScope)
    uiScope.launch {
      combine(model.projectPathFlows.projectPathWithDefault, executable) { projectPath, executable ->
        projectPath to executable.takeIf { needsExecutable }
      }.distinctUntilChanged()
        .collectLatest { [projectPath, selectedExecutable] ->
          val [workspace, environments, reservedNames] = withContext(Dispatchers.IO) {
            val executable = if (needsExecutable) {
              selectedExecutable ?: model.fileSystem.resolveExecutable(tool)
            }
            else null
            val workspace = findPixiWorkspace(projectPath)
            val environments = workspace?.environments.orEmpty().mapNotNull { name ->
              checkNotNull(workspace)
              val environment = PixiEnvironment(workspace, name)
              if (!existingOnly || environment.exists(executable)) environment else null
            }
            Triple(workspace, environments, workspace?.let { pixiReservedEnvironmentNames(it.root).toSet() }.orEmpty())
          }
          withContext(Dispatchers.EDT) {
            val createsWorkspace = workspace == null && canInitializeWorkspace
            initializeWorkspace.set(createsWorkspace)
            editEnvironmentName.set(createsWorkspace)
            newWorkspaceRoot.set(projectPath.takeIf { createsWorkspace })
            reservedEnvironmentNames = reservedNames
            val previousSelection = selectedEnvironment.get()
            val availableEnvironments = buildList {
              addAll(environments)
              if (!existingOnly && workspace != null) add(PixiEnvironment(workspace, "", createNew = true))
            }
            environmentComboBox.removeAllItems()
            availableEnvironments.forEach(environmentComboBox::addItem)
            selectedEnvironment.set(availableEnvironments.firstOrNull { it == previousSelection } ?: environments.firstOrNull())
          }
        }
    }
  }

  fun selection(): PyResult<PixiSelection<P>> {
    val executable = toolExecutable.get()?.pathHolder?.getOr { return it }
                     ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.executable.not.selected"))
    if (initializeWorkspace.get() || createEnvironment.get()) {
      val environment = initialEnvironmentName.get().trim()
      if (environment.isEmpty()) {
        return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.name.required"))
      }
      if (!environment.matches(ENVIRONMENT_NAME_PATTERN)) {
        return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.name.invalid"))
      }
      if (createEnvironment.get() && environment in reservedEnvironmentNames) {
        return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.name.taken", environment))
      }
      val root = newWorkspaceRoot.get() ?: selectedEnvironment.get()?.workspace?.root
                 ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.not.selected"))
      return Result.success(PixiSelection(
        executable, PixiWorkspace(root, listOf(environment)), environment,
        initializeWorkspace = initializeWorkspace.get(), pythonVersion = selectedPythonVersion.get(),
        createEnvironment = createEnvironment.get(),
      ))
    }
    val environment = selectedEnvironment.get()
                      ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.not.selected"))
    return Result.success(PixiSelection(executable, environment.workspace, environment.name, initializeWorkspace = false))
  }

  suspend fun createSdk(moduleOrProject: ModuleOrProject, selection: PixiSelection<P>): PyResult<PythonInterpreter> {
    val pyProject = moduleOrProject.findPyProject()
    when (model.fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> if (pyProject != null) {
        val ref = PyInterpreterRef.native(PixiPyTool.getInstance().fusId, PyEnvRef(selection.environment))
        return PythonInterpreterRegistry.getInstance(moduleOrProject.project).newPythonInterpreter(pyProject, ref)
      }
      is EelOrTarget.IsTarget -> Unit
    }

    val home = workspacePathResolver(selection.executable, selection.workspace.root, ".pixi/envs/${selection.environment}")
      .getOr { return it }
    val binary = model.fileSystem.resolvePythonBinary(home)
                 ?: return PyResult.localizedError(PyPixiBackendBundle.message("python.pixi.add.environment.not.available"))
    return model.fileSystem.setupSdk(
      moduleOrProject = moduleOrProject,
      pythonBinaryPath = binary,
      sdkAdditionalData = createPixiSdkData(selection.workspace.root),
      targetPanelExtension = model.state.targetPanelExtension.get(),
      suggestedSdkName = null,
    )
  }

  private suspend fun PixiEnvironment.exists(executable: P?): Boolean {
    val home = workspacePathResolver(executable, workspace.root, ".pixi/envs/$name").successOrNull ?: return false
    return when (model.fileSystem.eelOrTarget) {
      is EelOrTarget.IsEel -> model.fileSystem.directoryExists(home)
      is EelOrTarget.IsTarget -> {
        val binary = model.fileSystem.resolvePythonBinary(home) ?: return false
        pixiFileExists(model.fileSystem, binary).successOrNull == true
      }
    }
  }
}

private data class PixiEnvironment(val workspace: PixiWorkspace, val name: String, val createNew: Boolean = false)

internal data class PixiSelection<P : PathHolder>(
  val executable: P,
  val workspace: PixiWorkspace,
  val environment: String,
  val initializeWorkspace: Boolean,
  val pythonVersion: String? = null,
  val createEnvironment: Boolean = false,
)

private val ENVIRONMENT_NAME_PATTERN = Regex("[a-z0-9-]+")
