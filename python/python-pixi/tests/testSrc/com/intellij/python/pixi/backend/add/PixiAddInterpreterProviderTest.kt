// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pixi.backend.add

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetBrowserHints
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.python.junit5Tests.framework.applicationScope
import com.intellij.python.pixi.backend.PixiPyTool
import com.intellij.python.pytools.frontend.PackageManagerPyToolFrontend
import com.intellij.python.pytools.frontend.PyToolFrontend
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.backend.PyExecutable
import com.intellij.python.pytools.backend.PyExecutableCache
import com.intellij.python.pytools.backend.PyToolManager
import com.intellij.python.pytools.backend.ToolCommandSpec
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.replacedServiceFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.errorProcessing.ErrorSink
import com.jetbrains.python.errorProcessing.PyErrorDetail
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.isFailure
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.ToolProbeResult
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.FileSystemFactory
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.InterpreterCreationContext
import com.jetbrains.python.sdk.add.v2.PythonLocalAddInterpreterModel
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterProvider
import com.jetbrains.python.sdk.add.v2.PythonAddInterpreterProviderContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.InstanceOfAssertFactories.type
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.event.ActionListener
import java.nio.file.Path
import javax.swing.JTextField
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Subsystems.Interpreters
@Layers.Functional
internal class PixiAddInterpreterProviderTest {
  private val scope = applicationScope()
  private val executableCache = ApplicationManager.getApplication().replacedServiceFixture(PyExecutableCache::class.java) {
    object : PyExecutableCache {
      override suspend fun get(eelDescriptor: EelDescriptor, executable: PyExecutable): Path? = null
      override fun invalidate(eelDescriptor: EelDescriptor, executable: PyExecutable) = Unit
    }
  }

  @BeforeEach
  fun useMissingExecutableCache() {
    executableCache.get()
  }

  @Test
  fun `the existing target selector retries lookup after an executable change`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    projectPath.resolve("pixi.toml").writeText("[workspace]\n")
    val target = object : TargetEnvironmentConfiguration("pixi-test") {
      override var projectRootOnTarget: String = "/remote project"
    }
    val fileSystem = object : FileSystem<PathHolder.Target> by FileSystemFactory.getInstance().create(target) {
      override val isBrowsable: Boolean = false

      override fun createBrowseFolderListener(
        textField: TextFieldWithBrowseButton,
        descriptor: FileChooserDescriptor,
        fieldAccessor: TextComponentAccessor<JTextField>,
        targetHints: TargetBrowserHints,
        title: String,
      ): ActionListener? = null

      override suspend fun detectTool(toolSpec: ToolCommandSpec, filter: (PathHolder.Target) -> Boolean): PathHolder.Target? = null

      override suspend fun probeTools(toolSpecs: List<ToolCommandSpec>): PyResult<Map<String, ToolProbeResult<PathHolder.Target>>> =
        PyResult.success(emptyMap())
    }
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), fileSystem)
    val initialLookup = CompletableDeferred<Unit>()
    val selectedLookup = CompletableDeferred<Triple<PathHolder.Target, Path, String>>()
    val form = PixiEnvironmentForm(model, existingOnly = true, workspacePathResolver = { executable, root, relativePath ->
      if (executable == null) initialLookup.complete(Unit)
      else selectedLookup.complete(Triple(executable, root, relativePath))
      PyResult.localizedError("The test workspace lookup has no mapped environment.")
    })
    withContext(Dispatchers.EDT) {
      panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
      form.onShown(scope.get())
    }
    withTimeout(5.seconds) { initialLookup.await() }
    waitUntil("The executable detection must finish", 5.seconds) { !form.toolValidator.isValidationInProgress }
    val executable = PathHolder.Target("/custom/pixi")

    withContext(Dispatchers.EDT) { form.setToolExecutable(executable) }

    assertThat(withTimeout(5.seconds) { selectedLookup.await() })
      .isEqualTo(Triple(executable, projectPath, ".pixi/envs/default"))
  }

  @Test
  fun `a new environment proposes an unused name and rejects reserved names`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    projectPath.resolve("pixi.toml").writeText("[workspace]\n[feature.env]\n[environments]\nenv-2 = []\n")
    projectPath.resolve(".pixi/envs/env-3").createDirectories()
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = false, tool = PixiPyTool())
    val dialogPanel = withContext(Dispatchers.EDT) {
      panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
    }
    withContext(Dispatchers.EDT) {
      form.onShown(scope.get())
      form.selectExecutableAfterDetection(PathHolder.Eel(projectPath.resolve("pixi")))
    }
    waitUntil("The form must offer named environment creation", 5.seconds) {
      withContext(Dispatchers.EDT) {
        UIUtil.findComponentsOfType(dialogPanel, ComboBox::class.java).first().itemCount == 3
      }
    }
    withContext(Dispatchers.EDT) {
      UIUtil.findComponentsOfType(dialogPanel, ComboBox::class.java).first().selectedIndex = 2
    }
    val selection = form.selection().getOrThrow()
    assertThat(selection.environment).isEqualTo("env-4")
    assertThat(selection.createEnvironment).isTrue()
    val nameField = withContext(Dispatchers.EDT) {
      UIUtil.findComponentsOfType(dialogPanel, JTextField::class.java).single { it.text == "env-4" }
    }
    for (reservedName in listOf("default", "env", "env-2", "env-3")) {
      withContext(Dispatchers.EDT) { nameField.text = reservedName }
      assertThat(form.selection().isFailure).describedAs(reservedName).isTrue()
    }
  }

  @Test
  fun `the add interpreter form can create a named environment with a Python version`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    projectPath.resolve("pixi.toml").writeText("[workspace]\n")
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = false, tool = PixiPyTool())
    val dialogPanel = withContext(Dispatchers.EDT) {
      panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
    }
    withContext(Dispatchers.EDT) {
      form.onShown(scope.get())
      form.selectExecutableAfterDetection(PathHolder.Eel(projectPath.resolve("pixi")))
    }
    waitUntil("The form must offer named environment creation", 5.seconds) {
      withContext(Dispatchers.EDT) {
        UIUtil.findComponentsOfType(dialogPanel, ComboBox::class.java).first().itemCount == 2
      }
    }
    withContext(Dispatchers.EDT) {
      val combos = UIUtil.findComponentsOfType(dialogPanel, ComboBox::class.java)
      combos.first().selectedIndex = 1
      UIUtil.findComponentsOfType(dialogPanel, JTextField::class.java).single { it.text == "env" }.text = "docs"
      combos.last().selectedItem = "3.13"
    }
    val selection = form.selection().getOrThrow()
    assertThat(selection.environment).isEqualTo("docs")
    assertThat(selection.initializeWorkspace).isFalse()
    assertThat(selection.pythonVersion).isEqualTo("3.13")
    assertThat(selection.createEnvironment).isTrue()
  }

  @Test
  fun `the plugin registers the interpreter provider and package manager presentation`() {
    val providers = PythonAddInterpreterProvider.EP_NAME.extensionList.filterIsInstance<PixiAddInterpreterProvider>()

    assertThat(providers).singleElement().isInstanceOf(PixiAddInterpreterProvider::class.java)
    assertThat(providers.single().manager.supports(EelFileSystem(localEel))).isTrue()
    assertThat(PyToolFrontend.findById(PixiPyTool.getInstance().fusId))
      .asInstanceOf(type(PackageManagerPyToolFrontend::class.java))
      .extracting { it.presentableName }.isEqualTo("Pixi")
  }

  @Test
  fun `the Pixi provider is available in all interpreter dialogs`() {
    val provider = PixiAddInterpreterProvider()

    assertThat(provider.supports(InterpreterCreationContext.ADD_INTERPRETER)).isTrue()
    assertThat(provider.supports(InterpreterCreationContext.NEW_PROJECT_WIZARD)).isTrue()
  }

  @Test
  fun `Add Interpreter offers workspace creation without a manifest`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val creator = PixiAddInterpreterProvider().createNewEnvironment(PythonAddInterpreterProviderContext(
      model, null, ErrorSink { error("Workspace selection must not report an error.") }, InterpreterCreationContext.ADD_INTERPRETER,
    ))
    val dialogPanel = withContext(Dispatchers.EDT) {
      panel { creator.setupUI(this, DialogValidationRequestor { _, _ -> }) }
    }
    withContext(Dispatchers.EDT) { creator.onShown(scope.get()) }

    waitUntil("Add Interpreter must offer an initial environment name", 5.seconds) {
      withContext(Dispatchers.EDT) {
        UIUtil.findComponentsOfType(dialogPanel, JTextField::class.java).any { it.text == "default" && it.isVisible }
      }
    }
    val pythonVersions = withContext(Dispatchers.EDT) {
      UIUtil.findComponentsOfType(dialogPanel, ComboBox::class.java).single { it.isVisible }.let { combo ->
        (0 until combo.itemCount).map(combo::getItemAt)
      }
    }
    assertThat(pythonVersions).contains("3.13")
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `the executable field offers Pixi installation`(existingOnly: Boolean, @TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = existingOnly, tool = PixiPyTool())
    val links = withContext(Dispatchers.EDT) {
      val dialogPanel = panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
      UIUtil.findComponentsOfType(dialogPanel, ActionLink::class.java).map { it.text }
    }

    assertThat(links.filter { it == PyBundle.message("sdk.create.custom.venv.install.fix.title", "Pixi") })
      .containsExactly(PyBundle.message("sdk.create.custom.venv.install.fix.title", "Pixi"))
  }

  @Test
  fun `the installation link uses the tool manager and reports its failure`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val installedOn = mutableListOf<EelDescriptor>()
    val errors = mutableListOf<PyErrorDetail>()
    val tool = object : PyTool by PixiPyTool() {
      override val manager = object : PyToolManager {
        override suspend fun install(eel: EelApi): PyResult<Path> {
          installedOn.add(eel.descriptor)
          return PyResult.localizedError("The test installation failed.")
        }

        override suspend fun upgrade(eel: EelApi): PyResult<Path> = error("The installation link must not upgrade Pixi.")
      }
    }
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = false, tool = tool, errorSink = ErrorSink { errors.add(it) })
    withContext(Dispatchers.EDT) {
      val dialogPanel = panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
      UIUtil.findComponentsOfType(dialogPanel, ActionLink::class.java)
        .single { it.text == PyBundle.message("sdk.create.custom.venv.install.fix.title", "Pixi") }.doClick()
    }

    assertThat(installedOn).containsExactly(localEel.descriptor)
    assertThat(errors.map { it.error.message }).containsExactly("The test installation failed.")
  }

  @Test
  fun `the Pixi executable field validates a selected file`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val model = PythonLocalAddInterpreterModel(
      ProjectPathFlows.create(projectPath),
      EelFileSystem(localEel),
    )
    val form = PixiEnvironmentForm(model, existingOnly = false, tool = PixiPyTool())
    withContext(Dispatchers.EDT) {
      panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
      form.onShown(scope.get())
    }

    assertThatCode { form.toolValidator.validate(projectPath.resolve("pixi").toString()) }
      .doesNotThrowAnyException()
  }

  @Test
  fun `a new Pixi project requires an editable initial environment name`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val model = PythonLocalAddInterpreterModel(
      ProjectPathFlows.create(projectPath),
      EelFileSystem(localEel),
    )
    val form = PixiEnvironmentForm(
      model,
      existingOnly = false,
      canInitializeWorkspace = true,
      tool = PixiPyTool(),
    )
    val dialogPanel = withContext(Dispatchers.EDT) {
      panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
    }
    withContext(Dispatchers.EDT) { form.onShown(scope.get()) }
    waitUntil("The form must offer a new workspace", 5.seconds) { form.initializesNewWorkspace }
    withContext(Dispatchers.EDT) {
      form.selectExecutableAfterDetection(PathHolder.Eel(projectPath.resolve("pixi")))
    }
    val environmentNameField = withContext(Dispatchers.EDT) {
      UIUtil.findComponentsOfType(dialogPanel, JTextField::class.java).single { it.text == "default" }
    }

    withContext(Dispatchers.EDT) { environmentNameField.text = "" }
    assertThat(form.selection().isFailure).isTrue()

    withContext(Dispatchers.EDT) { environmentNameField.text = "docs" }
    val selection = form.selection().getOrThrow()
    assertThat(selection.environment).isEqualTo("docs")
    assertThat(selection.workspace.root).isEqualTo(projectPath)
    assertThat(selection.initializeWorkspace).isTrue()
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "   ", "two words", "../other", "feature.name", "docs/test", "Upper", "docs_test"])
  fun `a new workspace rejects an invalid environment name`(name: String, @TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = false, canInitializeWorkspace = true, tool = PixiPyTool())
    val dialogPanel = withContext(Dispatchers.EDT) {
      panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
    }
    withContext(Dispatchers.EDT) { form.onShown(scope.get()) }
    waitUntil("The form must offer a new workspace", 5.seconds) { form.initializesNewWorkspace }
    withContext(Dispatchers.EDT) {
      form.selectExecutableAfterDetection(PathHolder.Eel(projectPath.resolve("pixi")))
    }
    withContext(Dispatchers.EDT) {
      UIUtil.findComponentsOfType(dialogPanel, JTextField::class.java).single { it.text == "default" }.text = name
    }

    assertThat(form.selection().isFailure).isTrue()
  }

  @ParameterizedTest
  @CsvSource("false, 3", "true, 1")
  fun `the existing selector lists only installed environments`(
    existingOnly: Boolean, expectedCount: Int, @TempDir projectPath: Path,
  ): Unit = timeoutRunBlocking {
    projectPath.resolve("pixi.toml").writeText("[workspace]\n[environments]\ndocs = ['docs']\n")
    projectPath.resolve(".pixi/envs/default").createDirectories()
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = existingOnly, tool = PixiPyTool())
    val combo = withContext(Dispatchers.EDT) {
      val dialogPanel = panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) }
      UIUtil.findComponentsOfType(dialogPanel, ComboBox::class.java).first()
    }
    withContext(Dispatchers.EDT) {
      form.onShown(scope.get())
      form.selectExecutableAfterDetection(PathHolder.Eel(projectPath.resolve("pixi")))
    }
    waitUntil("The form must list $expectedCount environments", 5.seconds) {
      withContext(Dispatchers.EDT) { combo.itemCount == expectedCount }
    }
    val selections = withContext(Dispatchers.EDT) {
      (0 until if (existingOnly) combo.itemCount else combo.itemCount - 1).map { index ->
        combo.selectedIndex = index
        form.selection().getOrThrow()
      }
    }

    assertThat(selections.map { it.environment }).containsExactlyElementsOf(if (existingOnly) listOf("default")
                                                                            else listOf("default",
                                                                                        "docs"))
    assertThat(selections.map { it.workspace.root }).containsOnly(projectPath)
    assertThat(selections.map { it.initializeWorkspace }).containsOnly(false)
  }

  @Test
  fun `a new workspace requires a Pixi executable`(@TempDir projectPath: Path): Unit = timeoutRunBlocking {
    val model = PythonLocalAddInterpreterModel(ProjectPathFlows.create(projectPath), EelFileSystem(localEel))
    val form = PixiEnvironmentForm(model, existingOnly = false, canInitializeWorkspace = true, tool = PixiPyTool())
    withContext(Dispatchers.EDT) { panel { form.setupUI(this, DialogValidationRequestor { _, _ -> }) } }
    withContext(Dispatchers.EDT) { form.onShown(scope.get()) }
    waitUntil("The form must offer a new workspace", 5.seconds) { form.initializesNewWorkspace }
    withContext(Dispatchers.EDT) { form.toolExecutable.set(null) }

    assertThat(form.selection().isFailure).isTrue()
  }

  private suspend fun <P : PathHolder> PixiEnvironmentForm<P>.selectExecutableAfterDetection(executable: P) {
    waitUntil("The executable detection must finish before the test selects a path", 5.seconds) {
      withContext(Dispatchers.EDT) { !toolValidator.isValidationInProgress }
    }
    withContext(Dispatchers.EDT) { setToolExecutable(executable) }
  }
}
