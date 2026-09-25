// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2.uv

import com.intellij.openapi.module.Module
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.runtime.PyToolRuntime
import com.intellij.python.uv.backend.UvPyTool
import com.intellij.python.uv.backend.cli.uv.UvInitVcs
import com.intellij.python.uv.backend.runtime.uvCli
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.AsyncProcessIcon
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.withProject
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.CustomNewEnvironmentCreator
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonMutableTargetAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.UV
import com.jetbrains.python.sdk.add.v2.ToolValidator
import com.jetbrains.python.sdk.add.v2.ValidatedPath
import com.jetbrains.python.sdk.add.v2.ValidatedPathField
import com.jetbrains.python.sdk.add.v2.VenvAlreadyExistsError
import com.jetbrains.python.sdk.add.v2.VenvExistenceValidationState
import com.jetbrains.python.sdk.add.v2.persistCustomToolPath
import com.jetbrains.python.sdk.add.v2.validatablePathField
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.uv.impl.createUvLowLevel
import com.jetbrains.python.sdk.uv.impl.validateAndCreateUvCli
import com.jetbrains.python.sdk.uv.setupNewUvSdkAndEnv
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.VirtualEnvReader
import io.github.z4kn4fein.semver.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * The `major.minor` head of a version as uv prints it. Taken as a prefix rather than parsed, because a pre-release
 * (`3.15.0b4`) is not a dotted triple and the combo shows the language level anyway.
 */
private val LANGUAGE_LEVEL_PREFIX: Regex = Regex("""^\d+\.\d+""")

/** The granularity uv's `--python` resolves at, and the granularity the version combo shows: `3.14`. */
private fun Version.languageLevel(): @NlsSafe String = "$major.$minor"

/**
 * Creates a UV environment creator for the given model.
 *
 * @param module The module context for environment creation. Can be null when creating an interpreter
 *               at the project level (not associated with a specific module). When null, the creator
 *               will navigate to the generic Python existing environment selector instead of the
 *               UV-specific selector if a .venv directory already exists.
 */
internal fun PythonMutableTargetAddInterpreterModel<PathHolder.Eel>.uvCreator(module: Module?): EnvironmentCreatorUv<PathHolder.Eel> {
  val errorSink = module?.project?.let { ErrorSink().withProject(it) } ?: ErrorSink()
  return EnvironmentCreatorUv(this, module, errorSink)
}

internal class EnvironmentCreatorUv<P : PathHolder>(
  model: PythonMutableTargetAddInterpreterModel<P>,
  private val module: Module?,
  errorSink: ErrorSink,
) : CustomNewEnvironmentCreator<P>(model, errorSink) {
  override val interpreterType: InterpreterType = InterpreterType.UV
  override val pyTool: PyTool = UvPyTool.getInstance()
  override val pyToolPresentableName: String = "uv"
  override val toolValidator: ToolValidator<P> = model.uvViewModel.toolValidator
  override val globalSitePackage: Boolean get() = model.uvViewModel.inheritSitePackages.get()
  private val executableFlow = MutableStateFlow(model.uvViewModel.uvExecutable.get())
  private val pythonVersion: ObservableMutableProperty<Version?> = propertyGraph.property(null)
  private lateinit var versionComboBox: ComboBox<Version?>
  private lateinit var venvPathField: ValidatedPathField<Unit, P, ValidatedPath.Folder<P>>
  override val toolExecutable: ObservableProperty<ValidatedPath.Executable<P>?> = model.uvViewModel.uvExecutable
  override val toolExecutablePersister: suspend (P) -> Unit = { pathHolder ->
    model.fileSystem.persistCustomToolPath(pathHolder, pyTool)
  }

  private val venvAlreadyExistsError = propertyGraph.property<VenvAlreadyExistsError<P>?>(null)
  private val loading = AtomicBooleanProperty(false)

  /**
   * The language level uv reported it would pick, marked as the default in [versionComboBox] and pre-selected there, or
   * null while that is unknown. Written from the flow in [onShown] before the items are added, and read by the cell
   * renderer on the EDT, hence volatile.
   */
  @Volatile
  private var defaultLanguageLevel: String? = null

  init {
    model.uvViewModel.uvExecutable.afterChange {
      executableFlow.value = it
    }

    venvAlreadyExistsError.afterChange {
      if (it == null) {
        venvExistenceValidationState.set(VenvExistenceValidationState.Invisible)
      }
      else {
        val venvName = model.fileSystem.getVenvName(it.detectedSelectableInterpreter.homePath)
                       ?: VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME
        venvExistenceValidationState.set(VenvExistenceValidationState.Error(venvName))
      }
    }

    propertyGraph.dependsOn(venvAlreadyExistsError, model.uvViewModel.uvVenvPath, deleteWhenChildModified = false) {
      @Suppress("UNCHECKED_CAST") // TODO: Express it in the type-safe manner
      model.uvViewModel.uvVenvPath.get()?.validationResult?.errorOrNull as? VenvAlreadyExistsError<P>
    }
  }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    with(panel) {
      row(message("sdk.create.python.version")) {
        // One row per version, with uv's own pick marked rather than offered a second time as a separate "Default"
        // item: the two read as different options while producing the same environment. The marker is dropped when uv
        // could not say which version it would pick — an unmarked list is honest, a wrongly marked row is not.
        versionComboBox = comboBox(emptyList<Version?>(), textListCellRenderer("") { version ->
          val languageLevel = version.languageLevel()
          when (languageLevel) {
            defaultLanguageLevel -> message("python.sdk.uv.version.default.marker", languageLevel)
            else -> languageLevel
          }
        })
          .bindItem(pythonVersion)
          .enabledIf(loading.not())
          .component

        cell(AsyncProcessIcon("loader"))
          .align(AlignX.LEFT)
          .customize(UnscaledGaps(0))
          .visibleIf(loading)
      }

      executablePath = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = toolValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.venv.executable.path", "uv"),
        missingExecutableText = message("sdk.create.custom.venv.missing.text", "uv"),
        installAction = createInstallFix(errorSink)
      )

      venvPathField = validatablePathField(
        fileSystem = model.fileSystem,
        pathValidator = model.uvViewModel.uvVenvValidator,
        validationRequestor = validationRequestor,
        labelText = message("sdk.create.custom.location"),
        missingExecutableText = null,
        isFileSelectionMode = false,
        venvExistenceValidationState = venvExistenceValidationState,
      )

      row("") {
        venvExistenceValidationAlert(validationRequestor) {
          onVenvSelectExisting()
        }
      }

      row("") {
        checkBox(message("sdk.create.custom.inherit.packages"))
          .bindSelected(model.uvViewModel.inheritSitePackages)
          .comment(message("sdk.create.custom.uv.inherit.packages.comment"))
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    executablePath.initialize(scope)
    venvPathField.initialize(scope)
    model
      .projectPathFlows
      .projectPathWithDefault
      .combine(executableFlow) { projectPath, executable -> projectPath to executable }
      .onEach { (projectPath, executable) ->
        model.uvViewModel.uvVenvValidator.autodetectFolder()

        versionComboBox.removeAllItems()
        defaultLanguageLevel = null

        if (executable?.validationResult?.successOrNull == null) {
          return@onEach
        }

        try {
          loading.set(true)

          val pyProjectTomlPath = projectPath.resolve(PY_PROJECT_TOML)

          val pythonVersions = withContext(Dispatchers.IO) {
            val versionRequest = PyProjectToml.parseOrNull(pyProjectTomlPath)?.project?.requiresPython

            val cli = validateAndCreateUvCli(executable.pathHolder, model.fileSystem).getOr { return@withContext emptyList() }
            // The supported Python versions of uv do not depend on a directory, so this runs with none.
            val uvLowLevel = createUvLowLevel(cwd = null, cli, model.fileSystem, null)
            uvLowLevel.listSupportedPythonVersions(versionRequest)
              .getOr { return@withContext emptyList() }
          }

          // Resolved before the items are added so the renderer already knows which row to mark on its first paint.
          defaultLanguageLevel = executable.pathHolder?.let { uvExecutable ->
            withContext(Dispatchers.IO) { resolveDefaultLanguageLevel(uvExecutable, projectPath) }
          }

          pythonVersions.forEach {
            versionComboBox.addItem(it)
          }
          // uv's own pick, so that leaving the combo alone builds the environment uv would have built anyway. It is
          // routinely not the newest offered: a `.python-version` or a `requires-python` bound moves it, and even
          // within a satisfied range uv follows its own preference order rather than taking the highest.
          //
          // Where uv could not answer at all, the newest supported version is a deliberate choice and not a guess at
          // uv's — deterministic, visible in the combo, and the user's to change. Taken by comparison rather than as
          // the head of the list, which would rest on uv listing newest first.
          versionComboBox.selectedItem = pythonVersions.firstOrNull { it.languageLevel() == defaultLanguageLevel }
                                         ?: pythonVersions.maxOrNull()
        }
        finally {
          loading.set(false)
        }
      }
      .launchIn(scope)

  }

  /**
   * Asks uv which interpreter it would use for [projectPath], as the language level it reports, or null when it cannot
   * say — no interpreter satisfies the project's `requires-python`, or uv failed outright.
   *
   * Run from [projectPath] itself, or from the deepest directory above it that exists: uv walks up for
   * `.python-version` and for a `pyproject.toml` whose `requires-python` narrows the choice, so an ancestor of a
   * project directory yet to be created answers as that directory will once it is created there.
   *
   * `system = true` is what makes this a prediction rather than a report. Without it uv answers with the interpreter of
   * the `.venv` already in the directory, while the environment this dialog goes on to build is created by
   * `uv venv --clear`, which ignores that venv and takes uv's default instead.
   */
  private suspend fun resolveDefaultLanguageLevel(uvExecutable: P, projectPath: Path): String? {
    val workingDir = generateSequence(projectPath) { it.parent }.firstOrNull { candidate ->
      model.fileSystem.parsePath(candidate.toString()).successOrNull?.let { model.fileSystem.fileExists(it) } == true
    } ?: return null

    val runtime = PyToolRuntime(model.fileSystem.getBinaryToExec(uvExecutable, workingDir), ExecOptions())
    val reported = runtime.uvCli().python().find(showVersion = true, system = true).successOrNull ?: return null
    return LANGUAGE_LEVEL_PREFIX.find(reported)?.value
  }

  override fun onVenvSelectExisting() {
    PythonNewProjectWizardCollector.logExistingVenvFixUsed()

    if (module != null) {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = UV)
    }
    else {
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PYTHON)
    }
  }

  override suspend fun setupEnvSdk(moduleBasePath: Path): PyResult<Sdk> {
    val uv = toolExecutable.get()?.pathHolder!!
    return setupNewUvSdkAndEnv(
      uvExecutable = uv,
      workingDir = moduleBasePath,
      venvPath = model.uvViewModel.uvVenvPath.get()?.pathHolder,
      fileSystem = model.fileSystem,
      version = pythonVersion.get(),
      errorSink = errorSink,
      overrideExistingEnv = venvAlreadyExistsError.get() != null,
      inheritSitePackages = model.uvViewModel.inheritSitePackages.get(),
    )
  }

  /**
   * PY-92436: [createGitRepository] is forwarded rather than dropped. uv's `init` defaults to `--vcs git`, so with the
   * flag omitted the wizard produced a repository whichever way the user left the checkbox. The wizard's own
   * initializer still runs when the box is ticked, over the repository uv just made: `git init` is idempotent, and the
   * IDE appends to uv's `.gitignore` instead of replacing it, so the project ends up with both uv's Python ignores and
   * the IDE's VCS mapping.
   */
  override suspend fun createPythonModuleStructure(module: Module, createGitRepository: Boolean): PyResult<Unit> {
    val uv = toolExecutable.get()?.pathHolder!!
    val baseDir = module.baseDir!!
    val runtime = PyToolRuntime(
      model.fileSystem.getBinaryToExec(uv),
      ExecOptions()
    ).withWorkingDirectory(baseDir.toNioPath())

    // This runs before `setupEnvSdk`, so it is this `uv init` that writes `requires-python` and `.python-version`, and
    // the one afterwards is skipped because `pyproject.toml` now exists. Named nothing, uv would pin both to whichever
    // interpreter it defaults to, and the environment `setupEnvSdk` then builds on the chosen version is undone by the
    // next sync that reads those two files back. Null is left for the case where the combo offered nothing to pick, and
    // `setupEnvSdk` omits `--python` for it too.
    val vcs = if (createGitRepository) UvInitVcs.GIT else UvInitVcs.NONE
    return runtime.uvCli().init(python = pythonVersion.get()?.languageLevel(), vcs = vcs).mapSuccess {
      // Refresh so the just-created project structure is visible in VFS as a source root for the welcome step.
      VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)

      baseDir.findChild("src")?.takeIf { it.isDirectory }?.let { srcDir ->
        ModuleRootModificationUtil.updateModel(module) { rootModel ->
          val contentEntry = rootModel.contentEntries.firstOrNull() ?: return@updateModel
          contentEntry.addSourceFolder(srcDir, false)
        }
      }
    }
  }
}
