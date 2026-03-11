@file:Suppress("TestFunctionName")

package com.intellij.mcpserver.toolsets

import com.intellij.mcpserver.McpToolsetTestBase
import com.intellij.mcpserver.toolsets.general.ExecutionToolset
import com.intellij.openapi.application.runWriteAction
import io.kotest.common.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class ExecutionToolsetTest : McpToolsetTestBase() {
  @Test
  fun get_run_configurations() = runBlocking {
    val runManager = getRunManager()
    val editableSettings = createEditableConfiguration(runManager)
    val editableConfiguration = getConfiguration(editableSettings)
    setDynamicLaunchOverrides(editableConfiguration)
    val nonEditableSettings = createNonEditableConfiguration(runManager)

    runWriteAction {
      addConfiguration(runManager, editableSettings)
      addConfiguration(runManager, nonEditableSettings)
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
  fun execute_run_configuration_with_dynamic_launch_overrides_on_unsupported_configuration() = runBlocking {
    val runManager = getRunManager()
    val nonEditableSettings = createNonEditableConfiguration(runManager)

    runWriteAction {
      addConfiguration(runManager, nonEditableSettings)
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

  private fun createEditableConfiguration(runManager: Any): Any {
    val name = "editable-config"
    val baseSettings = createNonEditableConfiguration(runManager)
    val baseConfiguration = getConfiguration(baseSettings)
    val factory = baseConfiguration.javaClass.getMethod("getFactory").invoke(baseConfiguration)
    val editableConfiguration = createEditableConfigurationProxy(baseConfiguration, name)
    return runManager.javaClass.methods
      .first { it.name == "createConfiguration" && it.parameterCount == 2 && it.parameterTypes[0].name == "com.intellij.execution.configurations.RunConfiguration" }
      .invoke(runManager, editableConfiguration, factory)
  }

  private fun getRunManager(): Any {
    val projectClass = Class.forName("com.intellij.openapi.project.Project")
    val runManagerClass = Class.forName("com.intellij.execution.RunManager")
    return runManagerClass.getMethod("getInstance", projectClass).invoke(null, project)
  }

  private fun createNonEditableConfiguration(runManager: Any): Any {
    val name = "compound-config"
    val configurationTypeClassName = "com.intellij.execution.compound.CompoundRunConfigurationType"
    val createConfigurationMethod = runManager.javaClass.methods.first {
      it.name == "createConfiguration" &&
      it.parameterCount == 2 &&
      it.parameterTypes[0] == String::class.java &&
      it.parameterTypes[1] == Class::class.java
    }
    return createConfigurationMethod.invoke(runManager, name, Class.forName(configurationTypeClassName))
  }

  private fun getConfiguration(settings: Any): Any = settings.javaClass.getMethod("getConfiguration").invoke(settings)

  private fun setDynamicLaunchOverrides(configuration: Any) {
    val parametersClass = Class.forName("com.intellij.execution.CommonProgramRunConfigurationParameters")
    parametersClass.getMethod("setProgramParameters", String::class.java).invoke(configuration, "--sample")
    parametersClass.getMethod("setWorkingDirectory", String::class.java).invoke(configuration, project.basePath)
    parametersClass.getMethod("setEnvs", Map::class.java).invoke(configuration, mapOf("FOO" to "bar"))
  }

  private fun addConfiguration(runManager: Any, settings: Any) {
    runManager.javaClass.methods
      .first { it.name == "addConfiguration" && it.parameterCount == 1 }
      .invoke(runManager, settings)
  }

  private fun createEditableConfigurationProxy(baseConfiguration: Any, name: String): Any {
    val runConfigurationClass = Class.forName("com.intellij.execution.configurations.RunConfiguration")
    val parametersClass = Class.forName("com.intellij.execution.CommonProgramRunConfigurationParameters")
    val envMap = linkedMapOf<String, String>()
    var programParameters: String? = null
    var workingDirectory: String? = null
    var passParentEnvs = true

    val invocationHandler = InvocationHandler { _, method, args ->
      when (method.name) {
        "setProgramParameters" -> {
          programParameters = args?.firstOrNull() as String?
          null
        }
        "getProgramParameters" -> programParameters
        "setWorkingDirectory" -> {
          workingDirectory = args?.firstOrNull() as String?
          null
        }
        "getWorkingDirectory" -> workingDirectory
        "setEnvs" -> {
          envMap.clear()
          @Suppress("UNCHECKED_CAST")
          envMap.putAll(args?.firstOrNull() as Map<String, String>)
          null
        }
        "getEnvs" -> LinkedHashMap(envMap)
        "setPassParentEnvs" -> {
          passParentEnvs = args?.firstOrNull() as Boolean
          null
        }
        "isPassParentEnvs" -> passParentEnvs
        "getName" -> name
        "setName" -> null
        "clone" -> createEditableConfigurationProxy(baseConfiguration, name)
        "equals" -> args?.firstOrNull() === baseConfiguration
        "hashCode" -> System.identityHashCode(baseConfiguration)
        "toString" -> "EditableTestRunConfigurationProxy($name)"
        else -> method.invoke(baseConfiguration, *(args ?: emptyArray()))
      }
    }

    return Proxy.newProxyInstance(
      javaClass.classLoader,
      arrayOf(runConfigurationClass, parametersClass),
      invocationHandler,
    )
  }
}
