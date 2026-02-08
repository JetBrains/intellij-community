// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.google.gson.JsonObject
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.apache.velocity.VelocityContext
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser.Companion.parseRange
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataState
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage
import org.jetbrains.plugins.gradle.jvmcompat.asSafeJsonArray
import org.jetbrains.plugins.gradle.jvmcompat.asSafeJsonObject
import org.jetbrains.plugins.gradle.jvmcompat.asSafeString
import org.jetbrains.plugins.gradle.util.Ranges

typealias PluginId = String
typealias PluginVersion = String
typealias PluginVersionsToGradleVersions = Map<PluginVersion, Ranges<GradleVersion>>

/**
 * This class is used to store compatibility between Gradle versions and Gradle plugins' versions.
 * If the version of a plugin is always same, use [org.jetbrains.kotlin.tools.projectWizard.Versions.GRADLE_PLUGINS]
 *
 */
@State(name = "GradleToPluginsCompatibilityStore", storages = [Storage("gradle-plugins-data.xml")])
class GradleToPluginsCompatibilityStore : IdeVersionedDataStorage<GradleToPluginsCompatibilityState>(
    parser = GradleToPluginsCompatibilityParser,
    defaultState = DEFAULT_GRADLE_PLUGINS_DATA
) {

    @Volatile
    private var compatibility: Map<PluginId, PluginVersionsToGradleVersions> = emptyMap()

    private fun applyState(state: GradleToPluginsCompatibilityState) {
        compatibility = getCompatibility(state)
    }

    init {
        applyState(DEFAULT_GRADLE_PLUGINS_DATA)
    }

    override fun newState(): GradleToPluginsCompatibilityState = GradleToPluginsCompatibilityState()

    private fun getCompatibility(data: GradleToPluginsCompatibilityState): Map<PluginId, PluginVersionsToGradleVersions> {
        val pluginIdToVersions = mutableMapOf<PluginId, PluginVersionsToGradleVersions>()
        data.plugins.forEach { plugin ->
            val pluginId = plugin.id ?: return@forEach
            val pluginVersionsToGradleVersions = mutableMapOf<PluginVersion, Ranges<GradleVersion>>()
            plugin.compatibility.forEach { mapping ->
                val pluginVersion = mapping.pluginVersion ?: return@forEach
                val gradleVersions = mapping.gradleVersions ?: return@forEach
                val gradleVersionsRange = parseRange(gradleVersions.split(','), GradleVersion::version)
                pluginVersionsToGradleVersions[pluginVersion] = gradleVersionsRange
            }
            pluginIdToVersions[pluginId] = pluginVersionsToGradleVersions
        }
        return pluginIdToVersions
    }

    fun getPluginVersionByGradleVersion(pluginId: String, gradleVersion: GradleVersion): String? {
        val compatibility = compatibility[pluginId] ?: return null
        compatibility.forEach { entry ->
            val compatibleGradleVersions = entry.value
            if (gradleVersion in compatibleGradleVersions) return entry.key
        }
        return null
    }

    fun getFoojayVersion(gradleVersion: GradleVersion): String? {
        return getPluginVersionByGradleVersion(FOOJAY_RESOLVER_PLUGIN_ID, gradleVersion)
    }

    companion object {

        @JvmStatic
        fun getInstance(): GradleToPluginsCompatibilityStore {
            return service()
        }

        fun getDefaultFoojayVersion(): String {
            return DEFAULT_FOOJAY_VERSION
        }
    }
}

internal fun GradleToPluginsCompatibilityState.provideDefaultDataContext(context: VelocityContext) {
    context.put("PLUGINS", this.plugins)
}

class GradleToPluginCompatibilityEntry() : BaseState() {
    constructor(id: String, compatibility: List<GradleToPluginVersionMapping>) : this() {
        this.id = id
        this.compatibility.addAll(compatibility)
    }

    var id: String? by string()
    var compatibility: MutableList<GradleToPluginVersionMapping> by list()
}

class GradleToPluginVersionMapping() : BaseState() {
    var gradleVersions by string()
    var pluginVersion by string()

    constructor(gradle: String, plugin: String) : this() {
        gradleVersions = gradle
        pluginVersion = plugin
    }
}

class GradleToPluginsCompatibilityState() : IdeVersionedDataState() {
    var plugins by list<GradleToPluginCompatibilityEntry>()

    constructor(pluginsList: List<GradleToPluginCompatibilityEntry>) : this() {
        plugins.addAll(pluginsList)
    }
}

internal object GradleToPluginsCompatibilityParser : IdeVersionedDataParser<GradleToPluginsCompatibilityState>() {

    override fun parseJson(data: JsonObject): GradleToPluginsCompatibilityState? {
        val pluginEntries = data.asSafeJsonObject?.get("plugins")?.asJsonArray ?: return null
        val parsedEntries = pluginEntries.map { jsonElement ->
            val obj = jsonElement.asSafeJsonObject ?: return null
            val id = obj["id"]?.asSafeString ?: return null

            val compatibilityObj = obj["compatibility"]?.asSafeJsonArray ?: return null

            val compatibility = compatibilityObj.mapNotNull { compatibilityEntry ->
                val compatibilityEntryObj = compatibilityEntry.asSafeJsonObject ?: return@mapNotNull null
                val gradleVersions = compatibilityEntryObj["gradle"]?.asSafeString ?: return@mapNotNull null
                val pluginVersion = compatibilityEntryObj["plugin"]?.asSafeString ?: return@mapNotNull null
                GradleToPluginVersionMapping(gradleVersions, pluginVersion)
            }

            GradleToPluginCompatibilityEntry(id, compatibility)
        }
        return GradleToPluginsCompatibilityState(parsedEntries)
    }
}

private const val FOOJAY_RESOLVER_PLUGIN_ID = "org.gradle.toolchains.foojay-resolver-convention"
private const val DEFAULT_FOOJAY_VERSION = "0.10.0"