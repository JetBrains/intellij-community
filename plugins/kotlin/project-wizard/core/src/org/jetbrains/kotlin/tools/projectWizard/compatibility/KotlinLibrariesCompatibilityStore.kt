// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.google.gson.JsonObject
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.apache.velocity.VelocityContext
import org.jetbrains.plugins.gradle.jvmcompat.*

class KotlinLibraryCompatibilityEntry(): BaseState() {
    constructor(groupId: String, artifactId: String, versions: Map<String, String>) : this() {
        this.artifactId = artifactId
        this.groupId = groupId
        this.versions.putAll(versions)
    }

    var artifactId: String? by string()
    var groupId: String? by string()
    var versions: MutableMap<String, String> by map()
}

class KotlinLibrariesCompatibilityState() : IdeVersionedDataState() {
    var libraries by list<KotlinLibraryCompatibilityEntry>()
    constructor(librariesList: List<KotlinLibraryCompatibilityEntry>): this() {
        libraries.addAll(librariesList)
    }
}

internal object KotlinLibrariesCompatibilityParser : IdeVersionedDataParser<KotlinLibrariesCompatibilityState>() {
    override fun parseJson(data: JsonObject): KotlinLibrariesCompatibilityState? {
        val libraryEntries = data.asSafeJsonObject?.get("libraries")?.asJsonArray ?: return null
        val parsedEntries = libraryEntries.map { jsonElement ->
            val obj = jsonElement.asSafeJsonObject ?: return null
            val artifactId = obj["artifactId"]?.asSafeString ?: return null
            val groupId = obj["groupId"]?.asSafeString ?: return null

            val versionsObj = obj["versions"].asSafeJsonObject ?: return null

            val versions = versionsObj.asMap().mapNotNull { (key, value) ->
                val version = value.asSafeString ?: return@mapNotNull null
                key to version
            }.toMap()

            KotlinLibraryCompatibilityEntry(groupId, artifactId, versions)
        }
        return KotlinLibrariesCompatibilityState(parsedEntries)
    }
}


/**
 * This store is used to store versions of Kotlin related libraries (mostly kotlinx).
 * Since they are often strongly tied to the Kotlin compiler version used, we store a version
 * for each Kotlin version to avoid incompatibilities.
 */
@State(name = "KotlinLibrariesCompatibilityStore", storages = [Storage("kotlin-wizard-data.xml")])
class KotlinLibrariesCompatibilityStore : IdeVersionedDataStorage<KotlinLibrariesCompatibilityState>(
    parser = KotlinLibrariesCompatibilityParser,
    defaultState = DEFAULT_KOTLIN_LIBRARIES_DATA
) {
    override fun newState(): KotlinLibrariesCompatibilityState = KotlinLibrariesCompatibilityState()

    private fun findLibraryEntry(groupId: String, artifactId: String): KotlinLibraryCompatibilityEntry? {
        // Try and find the library in the current state. If it does not exist, fallback to the default data.
        return state?.libraries?.firstOrNull {
            it.artifactId == artifactId && it.groupId == groupId
        } ?: DEFAULT_KOTLIN_LIBRARIES_DATA.libraries.firstOrNull {
            it.artifactId == artifactId && it.groupId == groupId
        }
    }

    /**
     * Returns the map of Kotlin version to the respective library version for the
     * library with the [groupId] and [artifactId].
     */
    fun getVersions(groupId: String, artifactId: String): Map<String, String>? {
        return findLibraryEntry(groupId, artifactId)?.let { return it.versions }
    }

    /**
     * Returns the latest library version for the [groupId] and [artifactId].
     * The latest version is the one defined for the highest Kotlin version.
     */
    fun getLatestVersion(groupId: String, artifactId: String): String? {
        return findLibraryEntry(groupId, artifactId)?.versions?.toList()?.maxBy { it.first }?.second
    }

    companion object {
        const val KOTLINX_GROUP = "org.jetbrains.kotlinx"
        const val COROUTINES_ARTIFACT_ID = "kotlinx-coroutines-core"
        const val DATETIME_ARTIFACT_ID = "kotlinx-datetime"
        const val SERIALIZATION_JSON_ARTIFACT_ID = "kotlinx-serialization-json"

        @JvmStatic
        fun getInstance(): KotlinLibrariesCompatibilityStore {
            return service()
        }
    }
}

internal fun KotlinLibrariesCompatibilityState.provideDefaultDataContext(context: VelocityContext) {
    context.put("LIBRARIES", this.libraries)
}