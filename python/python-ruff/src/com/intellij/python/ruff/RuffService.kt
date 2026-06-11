// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.python.community.execService.Args
import com.intellij.python.pytools.executeOn
import com.jetbrains.python.NON_INTERACTIVE_ROOT_TRACE_CONTEXT
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.ModuleOrProject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls

/**
 * Information about a Ruff configuration option.
 */
data class RuffConfigOptionInfo(
  /**
   * The documentation for the config option.
   */
  @param:Nls val doc: String,

  /**
   * The default value for the config option.
   */
  @param:Nls val default: String,

  /**
   * The type of value expected for the config option.
   */
  @param:Nls val valueType: String,

  /**
   * The scope of the config option.
   */
  @param:Nls val scope: String?,

  /**
   * An example of how to use the config option.
   */
  @param:Nls val example: String,

  /**
   * Whether the config option is deprecated.
   */
  @param:Nls val deprecated: String?,
)

/**
 * Information about a Ruff rule.
 */
data class RuffRuleInfo(
  @param:Nls val name: String,
  @param:Nls val code: String,
  @param:Nls val linter: String,
  val summary: String,
  val fix: String,
  /**
   * formatted in Markdown
   */
  val explanation: String,
  val preview: Boolean,
)

/**
 * Service for fetching and storing Ruff configuration options and rule information.
 */
@Service(Service.Level.PROJECT)
class RuffService(val project: Project, val cs: CoroutineScope) {
  private var _configOptions: Map<String, RuffConfigOptionInfo>? = null

  var configOptions: Map<String, RuffConfigOptionInfo>
    get() {
      _configOptions?.let { return it }
      cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { gatherConfigOptionInformation() }
      return _configOptions ?: emptyMap()
    }
    private set(value) {
      _configOptions = value
    }

  private var _configOptionGroups: Set<String>? = null
  var configOptionGroups: Set<String>
    get() {
      _configOptionGroups?.let { return it }
      cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { gatherConfigOptionInformation() }
      return _configOptionGroups ?: emptySet()
    }
    private set(value) {
      _configOptionGroups = value
    }

  private var _ruleInformation: Map<String, RuffRuleInfo>? = null
  var ruleInformation: Map<String, RuffRuleInfo>
    get() {
      _ruleInformation?.let { return it }
      cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { gatherRuleInformation() }
      return _ruleInformation ?: emptyMap()
    }
    private set(value) {
      _ruleInformation = value
    }

  private var _linterInformation: Map<String, String>? = null
  var linterInformation: Map<String, String>
    get() {
      _linterInformation?.let { return it }
      cs.launch(NON_INTERACTIVE_ROOT_TRACE_CONTEXT) { gatherRuleInformation() }
      return _linterInformation ?: emptyMap()
    }
    private set(value) {
      _linterInformation = value
    }

  companion object {
    private val LOG = logger<RuffService>()
  }

  /**
   * Fetches all Ruff configuration options from the Ruff executable.
   *
   * @return A map of option paths to their information, or an empty map if fetching fails.
   */
  suspend fun gatherConfigOptionInformation() {
    val output = RuffPyTool.getInstance().executeOn(
      ModuleOrProject.ProjectOnly(project),
      Args("config", "--output-format=json")
    ).orLogException(LOG)

    output?.let { loadConfigOptionInformation(output) }
  }

  /**
   * Parses the JSON output from the Ruff config command containing all configuration options.
   *
   * @param jsonString The JSON output from the Ruff config command.
   * @return A map of option paths to their information, or an empty map if parsing fails.
   */
  fun loadConfigOptionInformation(@Language("JSON") jsonString: String) {
    try {
      val jsonElement = JsonParser.parseString(jsonString)
      val jsonObject = jsonElement as? JsonObject ?: return

      configOptions = buildMap {
        jsonObject.entrySet().forEach { (key, value) ->
          processConfigOption(key, value, this)
        }
      }
      configOptionGroups = configOptions.keys.asSequence()
        .filter { "." in it }
        .map { option -> option.dropLastWhile { it != '.' }.dropLast(1) }
        .toSet()
    }
    catch (e: Exception) {
      LOG.warn("Error parsing Ruff config options JSON", e)
    }
  }

  /**
   * Processes a configuration option from the JSON output.
   *
   * @param path The path to the configuration option.
   * @param value The JSON value for the configuration option.
   * @param result The map to add the processed option to.
   */
  private fun processConfigOption(path: String, value: JsonElement, result: MutableMap<String, RuffConfigOptionInfo>) {
    if (value !is JsonObject) {
      return
    }

    // Check if this is a config option, or a nested object
    if (!value.has("doc")) return

    try {
      result[path] = RuffConfigOptionInfo(
        doc = value.get("doc").asString,
        default = value.get("default")?.let { if (it.isJsonNull) "" else it.asString } ?: "",
        valueType = value.get("value_type")?.let { if (it.isJsonNull) "" else it.asString } ?: "",
        scope = value.get("scope")?.let { if (it.isJsonNull) null else it.asString },
        example = value.get("example")?.let { if (it.isJsonNull) "" else it.asString } ?: "",
        deprecated = value.get("deprecated")?.let {
          if (it.isJsonNull) null
          else
            it.asJsonObject.get("message").asString
        }
      )
    }
    catch (e: Exception) {
      LOG.warn("Error parsing config option at path $path", e)
    }
    value.entrySet().forEach { (nestedKey, nestedValue) ->
      if (nestedValue is JsonObject) {
        processConfigOption("$path.$nestedKey", nestedValue, result)
      }
    }
  }

  /**
   * Gathers rule information from the Ruff executable.
   */
  suspend fun gatherRuleInformation() {
    val output = RuffPyTool.getInstance().executeOn(
      ModuleOrProject.ProjectOnly(project),
      Args("rule", "--output-format", "json", "--all")
    ).orLogException(LOG)

    output?.let { loadRuleInformation(it) }
  }

  /**
   * Loads rule information from a JSON string.
   * This method is primarily used for testing.
   *
   * @param jsonString The JSON string containing rule information.
   */
  fun loadRuleInformation(@Language("JSON") jsonString: String) {
    val jsonArray = JsonParser.parseString(jsonString) as? JsonArray ?: return
    ruleInformation = jsonArray.associate { item ->
      item as JsonObject
      val code = item.get("code").asString
      code to RuffRuleInfo(
        name = item.get("name").asString,
        code = code,
        linter = item.get("linter").asString,
        summary = item.get("summary").asString,
        fix = item.get("fix").asString,
        explanation = item.get("explanation").asString,
        preview = item.get("preview").asBoolean,
      )
    }
    linterInformation = ruleInformation.entries.associate { (key, value) ->
      key.takeWhile { it.isLetter() } to value.linter
    }
  }
}