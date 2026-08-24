// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run

import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jdom.Element

/**
 * ID of the removed standalone `uv run` configuration type.
 */
internal const val LEGACY_UV_CONFIGURATION_ID: String = "UvRunConfigurationType"

/**
 * Marks the element [convertLegacyUvRunConfiguration] has retyped, so that [applyLegacyUvRunConfiguration] knows to read
 * the legacy options out of it. Never reaches disk: the stored form is serialized from the configuration, not from the
 * element being loaded, and the attribute is consumed as soon as it is applied.
 */
private const val LEGACY_UV_MIGRATION_ATTRIBUTE: String = "uvRunMigration"

internal enum class LegacyUvRunType {
  SCRIPT,
  MODULE,
}

/**
 * Options of the removed standalone `uv run` configuration, retained so that stored configurations can still be read.
 * The defaults must match the removed ones, because an option missing from the stored element keeps its default here.
 */
internal data class LegacyUvRunConfigurationOptions(
  var runType: LegacyUvRunType = LegacyUvRunType.SCRIPT,
  var scriptOrModule: String = "",
  var args: List<String> = emptyList(),
  var env: Map<String, String> = emptyMap(),
  var checkSync: Boolean = true,
  var uvSdkKey: String? = null,
  var uvArgs: List<String> = emptyList(),
  var debugJustMyCode: Boolean = false,
)

/**
 * Retypes a stored standalone `uv run` configuration as an ordinary Python one so that it reaches
 * [PythonRunConfiguration], which then takes over in [applyLegacyUvRunConfiguration].
 *
 * Only what the settings layer owns is rewritten here — the type, the factory and the name — because a converter runs
 * without a project and so cannot build the configuration whose fields hold the rest.
 *
 * @return `true` when [element] was a standalone `uv run` configuration and has been retyped
 */
internal fun convertLegacyUvRunConfiguration(element: Element): Boolean {
  if (element.getAttributeValue("type") != LEGACY_UV_CONFIGURATION_ID) return false
  // The standalone type is gone, so its template has nothing to become, and retyping it would replace the Python one.
  if (element.getAttributeValue("default").toBoolean()) return false

  val configurationType = PythonConfigurationType.getInstance()
  // This is needed so that we'll try to deserialize and read a proper new configuration type instead of a legacy one
  element.setAttribute("type", configurationType.id)
  element.setAttribute("factoryName", configurationType.factory.id)
  element.setAttribute(LEGACY_UV_MIGRATION_ATTRIBUTE, true.toString())
  return true
}

/**
 * Applies the options of a retyped standalone `uv run` configuration to [configuration] through its own setters, so the
 * migration never has to spell out how a Python run configuration serializes itself. Called while [configuration] reads
 * the element, and a no-op for every configuration that is not being migrated.
 *
 * `uvArgs` and `checkSync` have no counterpart on a Python run configuration and are dropped; the `uv run` flags the
 * former held can be expressed as `UV_*` environment variables instead. The removed configuration had no working
 * directory of its own either, so the migrated one resolves it the same way as any other Python configuration.
 */
internal fun applyLegacyUvRunConfiguration(element: Element, configuration: PythonRunConfiguration) {
  if (!element.getAttributeValue(LEGACY_UV_MIGRATION_ATTRIBUTE).toBoolean()) return
  element.removeAttribute(LEGACY_UV_MIGRATION_ATTRIBUTE)

  val options = LegacyUvRunConfigurationOptions()
  XmlSerializer.deserializeInto(options, element)

  // The removed type serialized nothing but its own options, so reading that element left every field a Python run
  // configuration owns unset - `null` where its constructor would have put a default. Build a proper configuration and
  // copy it over, rather than repairing [configuration] field by field.
  val migrated = PythonConfigurationType.getInstance().factory.createTemplateConfiguration(configuration.project) as PythonRunConfiguration
  migrated.scriptName = options.scriptOrModule
  migrated.scriptParameters = ParametersListUtil.join(options.args)
  migrated.isModuleMode = options.runType == LegacyUvRunType.MODULE
  // Merged rather than assigned, to keep the environment the configuration set up for itself.
  migrated.envs.putAll(options.env)
  migrated.sdk = options.uvSdkKey?.let { PythonSdkUtil.findSdkByKey(it) }
  migrated.setDebugJustMyCode(options.debugJustMyCode)
  migrated.useRunTool = true
  // Cast pins the PythonRunConfigurationParams overload, which copies the script fields too, not just the base ones.
  PythonRunConfiguration.copyParams(migrated as PythonRunConfigurationParams, configuration)

  // Not made unique against the other configurations: the run manager is loading its own state here, so asking it for
  // them would re-enter the service being initialized. A name already taken shadows the configuration that holds it,
  // which is what the platform does for any duplicate, and the shadowed one still exists on disk.
  if (configuration.name.isNotBlank()) {
    configuration.name = "uv run ${configuration.name}"
  }
}
