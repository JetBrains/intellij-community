// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.python.community.execService.Args
import com.intellij.python.pytools.backend.PyTool
import com.intellij.python.pytools.executeOn
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.findPythonSdk
import kotlinx.coroutines.CoroutineScope
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
   * formatted in Markdown. Ruff has no explanation for every rule.
   */
  val explanation: String?,
  val preview: Boolean,
)

/**
 * Service for fetching and storing Ruff configuration options and rule information.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class RuffService(val project: Project, val cs: CoroutineScope) {
  /**
   * Guards the check of [generation] against the publication it decides, which are two steps.
   * The answers themselves need no lock to read: each is one object behind one `@Volatile`.
   *
   * Both queries use it as their lock too. So one [invalidate] retires the answer and both queries in
   * one step, and a read cannot start a query between those steps that the retirement then cancels.
   */
  private val cacheLock = Any()
  private val configOptionQuery = RuffSharedQuery(cs, gate = cacheLock) { queryConfigOptionInformation() }
  private val ruleQuery = RuffSharedQuery(cs, gate = cacheLock) { queryRuleInformation() }
  private val generation = AtomicInteger()

  /**
   * The Ruff the cached answer describes: the version a server reported, and the module whose server
   * reported it. One reference, because a version paired with another module's answer is exactly the
   * staleness this key exists to catch. The module is held by name, because a project service
   * outlives a module and must not keep it. The module is also where a query runs, so the answer
   * comes from the binary that the server of the module runs.
   *
   * `null` until a server reports. An answer published while it is `null` came from a guessed scope,
   * so the first server to report replaces it.
   */
  private class AnswerSource(val version: String, val moduleName: String)

  private val answerSource = AtomicReference<AnswerSource?>(null)

  /**
   * One answer of `ruff config`. Both halves are derived from one output and are published as one,
   * so a reader can never see half an update. [generation] is the one it was published for: a reader
   * that finds an older one knows the answer was retired and starts the query that replaces it.
   */
  private class ConfigAnswer(val options: Map<String, RuffConfigOptionInfo>, val groups: Set<String>, val generation: Int)

  /** One answer of `ruff rule --all`. */
  private class RuleAnswer(val rules: Map<String, RuffRuleInfo>, val linters: Map<String, String>, val generation: Int)

  @Volatile
  private var configAnswer: ConfigAnswer? = null

  @Volatile
  private var ruleAnswer: RuleAnswer? = null

  val configOptions: Map<String, RuffConfigOptionInfo>
    get() = readConfigAnswer()?.options ?: emptyMap()

  val configOptionGroups: Set<String>
    get() = readConfigAnswer()?.groups ?: emptySet()

  val ruleInformation: Map<String, RuffRuleInfo>
    get() = readRuleAnswer()?.rules ?: emptyMap()

  val linterInformation: Map<String, String>
    get() = readRuleAnswer()?.linters ?: emptyMap()

  /**
   * The config answer to read, starting [query] when there is none or the one there was retired.
   *
   * A retired answer is still returned. It describes the Ruff of a moment ago, and rule names one
   * version old are better than none while the replacement runs. But the read must ask for that
   * replacement, because no other code fetches it, and the answer would outlive its Ruff. Only
   * some readers of this cache have an LSP server behind them: a `pyproject.toml` completion has
   * none, and would otherwise never see a newer Ruff.
   */
  private fun readConfigAnswer(): ConfigAnswer? {
    val answer = configAnswer
    if (answer == null || answer.generation != generation.get()) configOptionQuery.start()
    return answer
  }

  private fun readRuleAnswer(): RuleAnswer? {
    val answer = ruleAnswer
    if (answer == null || answer.generation != generation.get()) ruleQuery.start()
    return answer
  }

  /**
   * Whether the rule answer on hand was published for the current generation.
   *
   * This is the distinction a read acts on: a retired answer is still returned, and returning it is
   * what starts the query that replaces it.
   */
  @VisibleForTesting
  fun ruleAnswerIsCurrent(): Boolean = ruleAnswer?.generation == generation.get()

  /** The rule answer on hand, current or retired, without asking for a replacement. */
  @VisibleForTesting
  fun peekRuleInformation(): Map<String, RuffRuleInfo>? = ruleAnswer?.rules

  /**
   * Retires the cached answer, so the next [gatherInformation] replaces it.
   *
   * The answer stays readable, and the next read of it starts the query that replaces it. Keeping it
   * is deliberate: it describes a Ruff that is gone, but rule names one version old beat no rule
   * names at all, and a refresh that fails must not leave the reader with nothing. Bumping
   * [generation] is what retires it: a query still in flight ran against the Ruff being replaced, so
   * its answer is discarded rather than published over the new one.
   */
  fun invalidate() {
    val displaced = synchronized(cacheLock) {
      generation.incrementAndGet()
      listOfNotNull(configOptionQuery.retire(), ruleQuery.retire())
    }
    displaced.forEach { it.cancel() }
  }

  /**
   * Gathers the information of the Ruff that the LSP server of [module] runs. That server reports
   * [version].
   *
   * Every started LSP server calls this. A project has one server per module, and the servers start
   * as the files open. A refresh for each server would spend a Ruff process per module, which is the
   * bug that this cache prevents. [version] tells those servers apart from a different Ruff. The rule
   * catalogue and the config schema belong to the binary, so servers with one version share one
   * answer.
   *
   * [module] keeps the version correct. The query runs Ruff in that module, which is the scope where
   * the server resolved its own binary. So the answer and its version key come from one binary. A
   * query in project scope resolves the custom path, then `PATH`, then `uvx`, and never an
   * interpreter. So it can answer from a Ruff that no server runs. See [queryScope].
   *
   * A server that reports no version counts as one unknown Ruff, so the query runs one time, as
   * before.
   *
   * [ProjectLevelPyTool.onExecutableChanged] restarts the servers when the Ruff of a module can have changed, so
   * that they initialize and report again. These changes call it: a new custom path, an install or an
   * upgrade through [PyTool.manager], a new module SDK, and a new Ruff version in an SDK. The IDE sees
   * a Ruff that changes outside the IDE only when it reloads the package list of that SDK.
   *
   * A report that is not news retires nothing. It still renews the retry budget of a query that has
   * no answer, because a restarted server is a new chance for that query. A sibling module on another
   * Ruff also renews the budget. [RuffSharedQuery] still waits its retry delay between two attempts.
   */
  suspend fun gatherInformation(module: Module, version: String?) {
    if (!adoptSource(module.name, version)) {
      configOptionQuery.renewAttempts()
      ruleQuery.renewAttempts()
    }
    configOptionQuery.start().join()
    ruleQuery.start().join()
  }

  /**
   * Takes what [moduleName]'s server reports as the Ruff the cache describes, when it has something
   * to say that the cache does not already know, and retires the answer when it does.
   *
   * Only three things are news. Nothing has claimed the cache yet, so whatever answer it holds came
   * from a guessed scope. Or the module the answer came from now reports a different
   * version, which is its Ruff replaced under it. Or that module is gone, and someone has to take
   * over the scope a query runs in.
   *
   * Every other report comes from one of N servers that agree, or from a sibling module on its own
   * Ruff. Neither is a change. To retire for those costs a query per server start and gives nothing,
   * because the cache holds one answer however many versions the project runs.
   *
   * @return `true` when the cached answer was retired.
   */
  @VisibleForTesting
  suspend fun adoptSource(moduleName: String, version: String?): Boolean {
    val reported = version ?: UNKNOWN_VERSION
    while (true) {
      val current = answerSource.get()
      val news = when {
        current == null -> true
        current.moduleName == moduleName -> current.version != reported
        else -> !moduleExists(current.moduleName)
      }
      if (!news) return false
      if (answerSource.compareAndSet(current, AnswerSource(reported, moduleName))) {
        invalidate()
        return true
      }
    }
  }

  private suspend fun moduleExists(name: String): Boolean =
    readAction { ModuleManager.getInstance(project).findModuleByName(name) } != null

  /**
   * Where to run Ruff.
   *
   * [ModuleOrProject.ProjectOnly] looks like "this project's Ruff" and is not: `moduleIfExists` is
   * `null` for it, so `toolExecutableWithBaseArgs` skips the interpreter and resolves the custom
   * path, then `PATH`, then `uvx`. Every LSP server resolves its binary in module scope instead, so
   * once one has reported in, its module is the scope whose answer matches what the user sees.
   * Before that, and for a module that has since gone, [guessScope] picks the scope.
   */
  @VisibleForTesting
  suspend fun queryScope(): ModuleOrProject {
    val name = answerSource.get()?.moduleName ?: return guessScope()
    val module = readAction { ModuleManager.getInstance(project).findModuleByName(name) }
    if (module == null) {
      LOG.debug("Module $name is gone; guessing the scope of the Ruff query")
      return guessScope()
    }
    return ModuleOrProject.ModuleAndProject(module)
  }

  /**
   * The scope of a query that no server has claimed: the first module with a Python SDK.
   *
   * A read can come before any server reports. The inlay hints read the rule table when a file
   * opens. Project scope skips the interpreter, so it can find a `PATH` shim that fails on every
   * run, such as a pyenv shim. A module with a Python SDK resolves the Ruff of its interpreter first,
   * as the server of that module does. Project scope stays for a project with no such module.
   */
  private suspend fun guessScope(): ModuleOrProject {
    val modules = readAction { ModuleManager.getInstance(project).modules }
    val module = modules.firstOrNull { !it.isDisposed && it.findPythonSdk() != null }
                 ?: return ModuleOrProject.ProjectOnly(project)
    return ModuleOrProject.ModuleAndProject(module)
  }

  companion object {
    private val LOG = logger<RuffService>()

    /** Stands in for the version of a server that reports none, so one such Ruff is one key. */
    private const val UNKNOWN_VERSION = "<unknown>"
  }

  private suspend fun queryConfigOptionInformation(): Boolean {
    val started = generation.get()
    val output = RuffPyTool.getInstance().executeOn(
      queryScope(),
      Args("config", "--output-format=json")
    ).orLogException(LOG) ?: return false

    val parsed = parseConfigOptionInformation(output, started) ?: return false
    return synchronized(cacheLock) {
      // An invalidation overtook this query, so the output describes the Ruff it discarded.
      if (generation.get() != started) return@synchronized false
      configAnswer = parsed
      true
    }
  }

  /**
   * Parses the JSON output from the Ruff config command containing all configuration options.
   *
   * @param jsonString The JSON output from the Ruff config command.
   * @return A map of option paths to their information, or an empty map if parsing fails.
   */
  fun loadConfigOptionInformation(@Language("JSON") jsonString: String) {
    synchronized(cacheLock) { configAnswer = parseConfigOptionInformation(jsonString, generation.get()) ?: return }
  }

  /** Parses [jsonString] without touching the cache, so a caller can publish it under [cacheLock]. */
  private fun parseConfigOptionInformation(@Language("JSON") jsonString: String, generation: Int): ConfigAnswer? {
    try {
      val jsonElement = JsonParser.parseString(jsonString)
      val jsonObject = jsonElement as? JsonObject ?: return null

      val options = buildMap {
        jsonObject.entrySet().forEach { (key, value) ->
          processConfigOption(key, value, this)
        }
      }
      val groups = options.keys.asSequence()
        .filter { "." in it }
        .map { option -> option.dropLastWhile { it != '.' }.dropLast(1) }
        .toSet()
      return ConfigAnswer(options, groups, generation)
    }
    catch (e: Exception) {
      LOG.warn("Error parsing Ruff config options JSON", e)
      return null
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

  private suspend fun queryRuleInformation(): Boolean {
    val started = generation.get()
    val output = RuffPyTool.getInstance().executeOn(
      queryScope(),
      Args("rule", "--output-format", "json", "--all")
    ).orLogException(LOG) ?: return false

    val parsed = parseRuleInformation(output, started) ?: return false
    return synchronized(cacheLock) {
      // An invalidation overtook this query, so the output describes the Ruff it discarded.
      if (generation.get() != started) return@synchronized false
      ruleAnswer = parsed
      true
    }
  }

  /**
   * Loads rule information from a JSON string.
   * This method is primarily used for testing.
   *
   * @param jsonString The JSON string containing rule information.
   */
  fun loadRuleInformation(@Language("JSON") jsonString: String) {
    synchronized(cacheLock) { ruleAnswer = parseRuleInformation(jsonString, generation.get()) ?: return }
  }

  /** Parses [jsonString] without touching the cache, so a caller can publish it under [cacheLock]. */
  private fun parseRuleInformation(@Language("JSON") jsonString: String, generation: Int): RuleAnswer? {
    val jsonArray = try {
      JsonParser.parseString(jsonString) as? JsonArray ?: return null
    }
    catch (e: JsonParseException) {
      LOG.warn("Error parsing Ruff rules JSON", e)
      return null
    }

    val rules = jsonArray.mapNotNull { item ->
      val rule = item as? JsonObject ?: return@mapNotNull null
      // Ruff omits an optional field, or reports it as null. A rule without a code is not addressable.
      val code = rule.stringOrNull("code") ?: return@mapNotNull null
      val linter: @NlsSafe String = rule.stringOrNull("linter") ?: ""
      code to RuffRuleInfo(
        name = rule.stringOrNull("name") ?: code,
        code = code,
        linter = linter,
        summary = rule.stringOrNull("summary").orEmpty(),
        fix = rule.stringOrNull("fix").orEmpty(),
        explanation = rule.stringOrNull("explanation"),
        preview = rule.get("preview")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
      )
    }.toMap()

    val linters = rules.entries.associate { (key, value) ->
      key.takeWhile { it.isLetter() } to value.linter
    }
    return RuleAnswer(rules, linters, generation)
  }

  private fun JsonObject.stringOrNull(key: String): @NlsSafe String? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asString
}