@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.execution.CommonProgramRunConfigurationParameters
import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ExecutionToolset
import com.intellij.mcpserver.util.prepareRunConfigurationForExecution
import com.intellij.mcpserver.util.relativizeIfPossible
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EmptyIcon
import io.kotest.common.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import javax.swing.JPanel

class ExecutionToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_run_configurations() = runBlocking {
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
      assertTrue(editable.getValue("supportsDynamicLaunchOverrides").jsonPrimitive.content.toBoolean())
      assertEquals("--sample", editable.getValue("commandLine").jsonPrimitive.content)
      assertEquals(project.basePath, editable.getValue("workingDirectory").jsonPrimitive.content)
      assertEquals("bar", editable.getValue("environment").jsonObject.getValue("FOO").jsonPrimitive.content)

      val nonEditable = configurations.getValue("compound-config").jsonObject
      assertFalse(nonEditable.getValue("supportsDynamicLaunchOverrides").jsonPrimitive.content.toBoolean())
      assertFalse("commandLine" in nonEditable)
      assertFalse("workingDirectory" in nonEditable)
      assertFalse("environment" in nonEditable)
    }
  }

  @Test
  fun execute_run_configuration() = runBlocking {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("configurationName", JsonPrimitive("test-config"))
      },
      "Run configuration with name 'test-config' not found."
    )
  }

  @Test
  fun execute_run_configuration_from_context() = runBlocking {
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
  fun execute_run_configuration_rejects_mixed_targets() = runBlocking {
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
  fun execute_run_configuration_rejects_incomplete_context_target() = runBlocking {
    testMcpTool(
      ExecutionToolset::execute_run_configuration.name,
      buildJsonObject {
        put("filePath", JsonPrimitive(Path.of(requireNotNull(project.basePath)).relativizeIfPossible(mainJavaFile)))
      },
      "Pass both filePath and line together, or use configurationName."
    )
  }

  @Test
  fun execute_run_configuration_with_dynamic_launch_overrides_on_unsupported_configuration() = runBlocking {
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

    assertSame(editableConfiguration, preparedConfiguration)
    assertEquals("--sample", getProgramParameters(preparedConfiguration))
    assertEquals(project.basePath, getWorkingDirectory(preparedConfiguration))
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

    assertNotSame(editableConfiguration, preparedConfiguration)
    assertNull(getProgramParameters(preparedConfiguration))
    assertNull(getWorkingDirectory(preparedConfiguration))
    assertEquals("--sample", getProgramParameters(editableConfiguration))
    assertEquals(project.basePath, getWorkingDirectory(editableConfiguration))
  }

  private fun createEditableConfiguration(runManager: RunManager): RunnerAndConfigurationSettings {
    return runManager.createConfiguration("editable-config", EDITABLE_CONFIGURATION_TYPE.factory)
  }

  private fun createNonEditableConfiguration(runManager: RunManager): RunnerAndConfigurationSettings {
    return runManager.createConfiguration("compound-config", NON_EDITABLE_CONFIGURATION_TYPE.factory)
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
    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = NoOpSettingsEditor()

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = null
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

  companion object {
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
