@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionManager
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ExecutionToolset
import com.intellij.mcpserver.util.prepareRunConfigurationForExecution
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Conditions
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import javax.swing.JPanel

class ExecutionToolsetTest : McpToolsetTestBase() {
  private val mainKotlinFileFixture = sourceRootFixture.virtualFileFixture(
    "Main.kt",
    """
      suspend fun main() {
      }
    """.trimIndent(),
  )
  private val mainKotlinFile by mainKotlinFileFixture

  @Test
  fun get_run_configurations() = runBlocking(Dispatchers.Default) {
    val runManager = RunManager.getInstance(project)
    val editableSettings = createEditableConfiguration(runManager)
    setDynamicLaunchOverrides(editableSettings.configuration)
    val nonEditableSettings = createNonEditableConfiguration(runManager)

    runWriteAction {
      runManager.addConfiguration(editableSettings)
      runManager.addConfiguration(nonEditableSettings)
    }

    testMcpTool(
      ExecutionToolset::get_run_configurations.name,
      buildJsonObject {},
    ) { result ->
      val configurations = Json.parseToJsonElement(result.textContent.text)
        .jsonObject
        .getValue("configurations")
        .jsonArray
        .associateBy { it.jsonObject.getValue("name").jsonPrimitive.content }

      val editable = configurations.getValue("editable-config").jsonObject
      assertThat(editable.getValue("supportsDynamicLaunchOverrides").jsonPrimitive.content.toBoolean()).isTrue()
      assertThat(editable.getValue("commandLine").jsonPrimitive.content).isEqualTo("--sample")
      assertThat(editable.getValue("workingDirectory").jsonPrimitive.content).isEqualTo(project.basePath)
      assertThat(editable.getValue("environment").jsonObject.getValue("FOO").jsonPrimitive.content).isEqualTo("bar")

      val nonEditable = configurations.getValue("compound-config").jsonObject
      assertThat(nonEditable.getValue("supportsDynamicLaunchOverrides").jsonPrimitive.content.toBoolean()).isFalse()
      assertThat(nonEditable).doesNotContainKeys("commandLine", "workingDirectory", "environment")
    }
  }

  @Test
  fun get_run_configurations_collects_run_points_from_slow_markers() = runBlocking(Dispatchers.Default) {
    val mainKotlinPath = Path.of(requireNotNull(project.basePath)).relativizeIfPossible(mainKotlinFile)

    testMcpTool(
      ExecutionToolset::get_run_configurations.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(mainKotlinPath))
      },
    ) { result ->
      val response = Json.parseToJsonElement(result.textContent.text).jsonObject
      assertThat(response.getValue("filePath").jsonPrimitive.content).isEqualTo(mainKotlinPath)

      val runPoints = response.getValue("runPoints").jsonArray
      assertThat(runPoints).hasSize(1)
      assertThat(runPoints.single().jsonObject.getValue("line").jsonPrimitive.content.toInt()).isEqualTo(1)
    }
  }

  @Test
  fun execute_run_configuration() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("configurationName", JsonPrimitive("test-config"))
      },
      "Run configuration with name 'test-config' not found."
    )
  }

  @Test
  fun execute_run_configuration_from_context() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(Path.of(requireNotNull(project.basePath)).relativizeIfPossible(mainJavaFile)))
        put("line", JsonPrimitive(1))
      },
      "No run configuration could be created from src/Main.java:1. Use get_run_configurations with filePath to find valid run locations."
    )
  }

  @Test
  fun execute_run_configuration_rejects_mixed_targets() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("configurationName", JsonPrimitive("test-config"))
        put("filePath", JsonPrimitive(Path.of(requireNotNull(project.basePath)).relativizeIfPossible(mainJavaFile)))
        put("line", JsonPrimitive(1))
      },
      "Pass either configurationName or filePath + line, but not both."
    )
  }

  @Test
  fun execute_run_configuration_rejects_incomplete_context_target() = runBlocking(Dispatchers.Default) {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(Path.of(requireNotNull(project.basePath)).relativizeIfPossible(mainJavaFile)))
      },
      "Pass both filePath and line together, or use configurationName."
    )
  }

  @Test
  fun execute_run_configuration_with_dynamic_launch_overrides_on_unsupported_configuration() = runBlocking(Dispatchers.Default) {
    val runManager = RunManager.getInstance(project)
    val nonEditableSettings = createNonEditableConfiguration(runManager)

    runWriteAction {
      runManager.addConfiguration(nonEditableSettings)
    }

    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("configurationName", JsonPrimitive("compound-config"))
        put("programArguments", JsonPrimitive("--sample"))
      },
      "Run configuration 'compound-config' of type 'Compound' doesn't support dynamic launch overrides (programArguments, workingDirectory, envs)."
    )
  }

  @Test
  fun execute_run_configuration_returns_output_file_without_waiting_for_exit() = runBlocking(Dispatchers.Default) {
    val runManager = RunManager.getInstance(project)
    val settings = createExecutableConfiguration(
      runManager = runManager,
      name = "immediate-config",
      script = TestProcessScript(
        initialOutput = "started\n",
        delayBeforeExitMs = 300,
        finalOutput = "finished\n",
      ),
    )

    runWriteAction {
      runManager.addConfiguration(settings)
    }

    withTestProgramRunner {
      testMcpTool(
        ExecutionToolset::execute_run_configuration.name,
        buildJsonObject {
          put("configurationName", JsonPrimitive("immediate-config"))
          put("waitForExit", JsonPrimitive(false))
          put("timeout", JsonPrimitive(5_000))
        },
      ) { result ->
        val executionResult = Json.parseToJsonElement(result.textContent.text).jsonObject
        assertThat(executionResult).doesNotContainKey("exitCode")
        assertPublicExecutionResultDoesNotExposeInternalFields(executionResult)

        val logPath = Path.of(executionResult.getValue("fullOutputPath").jsonPrimitive.content)
        assertThat(logPath).exists()
        waitUntilAssertSucceedsBlocking(5.seconds) {
          assertThat(Files.readString(logPath)).contains("finished")
        }
        cleanupRunningDescriptors()
      }
    }
  }

  @Test
  fun execute_run_configuration_returns_output_file_when_timeout_expires() = runBlocking(Dispatchers.Default) {
    val runManager = RunManager.getInstance(project)
    val settings = createExecutableConfiguration(
      runManager = runManager,
      name = "timeout-config",
      script = TestProcessScript(
        initialOutput = "started\n",
        delayBeforeExitMs = 300,
        finalOutput = "finished\n",
      ),
    )

    runWriteAction {
      runManager.addConfiguration(settings)
    }

    withTestProgramRunner {
      testMcpTool(
        ExecutionToolset::execute_run_configuration.name,
        buildJsonObject {
          put("configurationName", JsonPrimitive("timeout-config"))
          put("timeout", JsonPrimitive(10))
        },
      ) { result ->
        val executionResult = Json.parseToJsonElement(result.textContent.text).jsonObject
        assertThat(executionResult).doesNotContainKey("exitCode")
        assertPublicExecutionResultDoesNotExposeInternalFields(executionResult)

        val logPath = Path.of(executionResult.getValue("fullOutputPath").jsonPrimitive.content)
        assertThat(logPath).exists()
        waitUntilAssertSucceedsBlocking(5.seconds) {
          assertThat(Files.readString(logPath)).contains("finished")
        }
        cleanupRunningDescriptors()
      }
    }
  }

  @Test
  fun execute_run_configuration_returns_exit_code_and_omits_output_file_for_small_output() = runBlocking(Dispatchers.Default) {
    val runManager = RunManager.getInstance(project)
    val settings = createExecutableConfiguration(
      runManager = runManager,
      name = "completed-config",
      script = TestProcessScript(
        initialOutput = "started\n",
        delayBeforeExitMs = 0,
        finalOutput = "finished\n",
        exitCode = 7,
      ),
    )

    runWriteAction {
      runManager.addConfiguration(settings)
    }

    withTestProgramRunner {
      testMcpTool(
        ExecutionToolset::execute_run_configuration.name,
        buildJsonObject {
          put("configurationName", JsonPrimitive("completed-config"))
          put("timeout", JsonPrimitive(5_000))
        },
      ) { result ->
        val executionResult = Json.parseToJsonElement(result.textContent.text).jsonObject
        assertThat(executionResult.getValue("exitCode").jsonPrimitive.content.toInt()).isEqualTo(7)
        assertThat(executionResult.getValue("output").jsonPrimitive.content).isEqualTo("started\nfinished\n")
        assertThat(executionResult).doesNotContainKey("fullOutputPath")
        assertPublicExecutionResultDoesNotExposeInternalFields(executionResult)
        cleanupRunningDescriptors()
      }
    }
  }

  @Test
  fun execute_run_configuration_keeps_output_file_after_exit_when_preview_is_truncated() = runBlocking(Dispatchers.Default) {
    val runManager = RunManager.getInstance(project)
    val longOutput = "a".repeat(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH + 50)
    val finalOutput = "\nfinished\n"
    val settings = createExecutableConfiguration(
      runManager = runManager,
      name = "truncated-config",
      script = TestProcessScript(
        initialOutput = longOutput,
        delayBeforeExitMs = 0,
        finalOutput = finalOutput,
      ),
    )

    runWriteAction {
      runManager.addConfiguration(settings)
    }

    withTestProgramRunner {
      testMcpTool(
        ExecutionToolset::execute_run_configuration.name,
        buildJsonObject {
          put("configurationName", JsonPrimitive("truncated-config"))
          put("timeout", JsonPrimitive(5_000))
        },
      ) { result ->
        val executionResult = Json.parseToJsonElement(result.textContent.text).jsonObject
        assertThat(executionResult.getValue("exitCode").jsonPrimitive.content.toInt()).isEqualTo(0)
        assertThat(executionResult.getValue("output").jsonPrimitive.content).isEqualTo(
          "a".repeat(Constants.RUN_CONFIGURATION_PREVIEW_MAX_LENGTH) + Constants.RUN_CONFIGURATION_PREVIEW_TRUNCATED_MARKER,
        )
        assertPublicExecutionResultDoesNotExposeInternalFields(executionResult)

        val logPath = Path.of(executionResult.getValue("fullOutputPath").jsonPrimitive.content)
        assertThat(logPath).exists()
        assertThat(Files.readString(logPath)).isEqualTo(longOutput + finalOutput)
        cleanupRunningDescriptors()
      }
    }
  }

  @Test
  fun prepare_run_configuration_for_execution_ignores_empty_string_overrides() {
    val editableSettings = createEditableConfiguration(RunManager.getInstance(project))
    val editableConfiguration = editableSettings.configuration
    setDynamicLaunchOverrides(editableConfiguration)

    val preparedConfiguration = prepareRunConfigurationForExecution(
      configurationName = "editable-config",
      configuration = editableConfiguration,
      programArguments = "",
      workingDirectory = "",
      envs = null,
    )

    assertThat(preparedConfiguration).isSameAs(editableConfiguration)
    assertThat(getProgramParameters(preparedConfiguration)).isEqualTo("--sample")
    assertThat(getWorkingDirectory(preparedConfiguration)).isEqualTo(project.basePath)
  }

  @Test
  fun prepare_run_configuration_for_execution_clears_values_for_blank_string_overrides() {
    val editableSettings = createEditableConfiguration(RunManager.getInstance(project))
    val editableConfiguration = editableSettings.configuration
    setDynamicLaunchOverrides(editableConfiguration)

    val preparedConfiguration = prepareRunConfigurationForExecution(
      configurationName = "editable-config",
      configuration = editableConfiguration,
      programArguments = " ",
      workingDirectory = " ",
      envs = null,
    )

    assertThat(preparedConfiguration).isNotSameAs(editableConfiguration)
    assertThat(getProgramParameters(preparedConfiguration)).isNull()
    assertThat(getWorkingDirectory(preparedConfiguration)).isNull()
    assertThat(getProgramParameters(editableConfiguration)).isEqualTo("--sample")
    assertThat(getWorkingDirectory(editableConfiguration)).isEqualTo(project.basePath)
  }

  private fun createEditableConfiguration(
    runManager: RunManager,
    name: String = "editable-config",
  ): RunnerAndConfigurationSettings {
    return runManager.createConfiguration(name, EDITABLE_CONFIGURATION_TYPE.factory)
  }

  private fun createNonEditableConfiguration(runManager: RunManager): RunnerAndConfigurationSettings {
    return runManager.createConfiguration("compound-config", NON_EDITABLE_CONFIGURATION_TYPE.factory)
  }

  private fun createExecutableConfiguration(
    runManager: RunManager,
    name: String,
    script: TestProcessScript,
  ): RunnerAndConfigurationSettings {
    val settings = createEditableConfiguration(runManager, name)
    (settings.configuration as TestRunConfiguration).stateFactory = { _, _ -> TestRunProfileState(script) }
    return settings
  }

  private suspend fun withTestProgramRunner(action: suspend () -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      ApplicationManager.getApplication().extensionArea.getExtensionPoint(ProgramRunner.PROGRAM_RUNNER_EP)
        .registerExtension(TEST_PROGRAM_RUNNER, disposable)
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun cleanupRunningDescriptors() {
    val executionManager = ExecutionManager.getInstance(project)
    executionManager.getRunningDescriptors(Conditions.alwaysTrue()).forEach { descriptor ->
      descriptor.processHandler?.destroyProcess()
    }
    waitUntilAssertSucceedsBlocking(5.seconds) {
      assertThat(executionManager.getRunningDescriptors(Conditions.alwaysTrue())).isEmpty()
    }
  }

  private fun assertPublicExecutionResultDoesNotExposeInternalFields(executionResult: JsonObject) {
    assertThat(executionResult).doesNotContainKeys("sessionId", "timedOut")
  }

  private fun setDynamicLaunchOverrides(configuration: RunConfiguration) {
    val configurable = configuration as CommonProgramRunConfigurationParameters
    configurable.programParameters = "--sample"
    configurable.workingDirectory = project.basePath
    configurable.envs = linkedMapOf("FOO" to "bar")
  }

  private fun getProgramParameters(configuration: RunConfiguration): String? {
    return (configuration as CommonProgramRunConfigurationParameters).programParameters
  }

  private fun getWorkingDirectory(configuration: RunConfiguration): String? {
    return (configuration as CommonProgramRunConfigurationParameters).workingDirectory
  }

  private open class TestRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
  ) : RunConfigurationBase<RunConfigurationOptions>(project, factory, name) {
    var stateFactory: ((Executor, ExecutionEnvironment) -> RunProfileState?)? = null

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = NoOpSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
      return stateFactory?.invoke(executor, environment)
    }
  }

  private class TestEditableRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
  ) : TestRunConfiguration(project, factory, name), CommonProgramRunConfigurationParameters {
    private var myProgramParameters: String? = null
    private var myWorkingDirectory: String? = null
    private val myEnvs = linkedMapOf<String, String>()
    private var myPassParentEnvs = true

    override fun setProgramParameters(value: String?) {
      myProgramParameters = value
    }

    override fun getProgramParameters(): String? = myProgramParameters

    override fun setWorkingDirectory(value: String?) {
      myWorkingDirectory = value
    }

    override fun getWorkingDirectory(): String? = myWorkingDirectory

    override fun setEnvs(envs: MutableMap<String, String>) {
      myEnvs.clear()
      myEnvs.putAll(envs)
    }

    override fun getEnvs(): MutableMap<String, String> = LinkedHashMap(myEnvs)

    override fun setPassParentEnvs(passParentEnvs: Boolean) {
      myPassParentEnvs = passParentEnvs
    }

    override fun isPassParentEnvs(): Boolean = myPassParentEnvs

    @Suppress("UsePropertyAccessSyntax")
    override fun clone(): TestEditableRunConfiguration {
      val cloned = super.clone() as TestEditableRunConfiguration
      cloned.programParameters = myProgramParameters
      cloned.workingDirectory = myWorkingDirectory
      cloned.envs = LinkedHashMap(myEnvs)
      cloned.setPassParentEnvs(myPassParentEnvs)
      return cloned
    }
  }

  private class TestNonEditableRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
  ) : TestRunConfiguration(project, factory, name)

  private class TestConfigurationType(
    id: String,
    displayName: String,
    description: String,
    private val configurationFactory: (project: Project, factory: ConfigurationFactory, name: String) -> RunConfiguration,
  ) : ConfigurationTypeBase(id, displayName, description, EmptyIcon.ICON_16) {
    val factory = object : ConfigurationFactory(this) {
      override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return configurationFactory(project, this, "")
      }

      override fun getId(): String = id
    }

    init {
      addFactory(factory)
    }
  }

  private class NoOpSettingsEditor : SettingsEditor<RunConfiguration>() {
    override fun resetEditorFrom(configuration: RunConfiguration) = Unit

    override fun applyEditorTo(configuration: RunConfiguration) = Unit

    override fun createEditor() = JPanel()
  }

  private data class TestProcessScript(
    val initialOutput: String = "",
    val delayBeforeExitMs: Long,
    val finalOutput: String = "",
    val exitCode: Int = 0,
  )

  private class TestRunProfileState(
    private val script: TestProcessScript,
  ) : RunProfileState {
    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
      val handler = TestProcessHandler(script)
      handler.schedule()
      return DefaultExecutionResult(null, handler)
    }
  }

  private class TestProcessHandler(
    private val script: TestProcessScript,
  ) : NopProcessHandler() {
    fun schedule() {
      ApplicationManager.getApplication().executeOnPooledThread {
        while (!isStartNotified) {
          Thread.sleep(5)
        }
        if (script.initialOutput.isNotEmpty()) {
          notifyTextAvailable(script.initialOutput, ProcessOutputTypes.STDOUT)
        }
        if (script.delayBeforeExitMs > 0) {
          Thread.sleep(script.delayBeforeExitMs)
        }
        if (script.finalOutput.isNotEmpty()) {
          notifyTextAvailable(script.finalOutput, ProcessOutputTypes.STDOUT)
        }
        notifyProcessTerminated(script.exitCode)
      }
    }
  }

  companion object {
    private val TEST_PROGRAM_RUNNER = object : GenericProgramRunner<RunnerSettings>() {
      override fun getRunnerId(): String = "ExecutionToolsetTestProgramRunner"

      override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return profile is TestRunConfiguration
      }

      override fun execute(environment: ExecutionEnvironment, callback: ProgramRunner.Callback?, state: RunProfileState) {
        try {
          val descriptor = doExecute(state, environment)
          if (descriptor == null) {
            callback?.processNotStarted(IllegalStateException("Execution result is null"))
          }
          else {
            callback?.processStarted(descriptor)
          }
        }
        catch (t: Throwable) {
          callback?.processNotStarted(t)
        }
      }

      override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null
        return RunContentBuilder(executionResult, environment).showRunContent(environment.contentToReuse)
      }
    }

    private val EDITABLE_CONFIGURATION_TYPE = TestConfigurationType(
      id = "EditableTestRunConfigurationType",
      displayName = "Editable",
      description = "Editable configuration",
    ) { project, factory, name ->
      TestEditableRunConfiguration(project, factory, name)
    }

    private val NON_EDITABLE_CONFIGURATION_TYPE = TestConfigurationType(
      id = "NonEditableTestRunConfigurationType",
      displayName = "Compound",
      description = "Compound configuration",
    ) { project, factory, name ->
      TestNonEditableRunConfiguration(project, factory, name)
    }
  }
}
