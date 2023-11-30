// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.google.gson.JsonObject
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gradle.jvmcompat.*

class DependencyVersionState() : IdeVersionedDataState() {
    constructor(versions: Map<String, String>) : this() {
        this.versions.putAll(versions)
    }

    var versions by map<String, String>()

    fun getVersion(key: String): String? {
        return versions[key]
    }
}

internal object DependencyVersionParser : IdeVersionedDataParser<DependencyVersionState>() {
    override fun parseJson(data: JsonObject): DependencyVersionState? {
        val obj = data.asSafeJsonObject ?: return null

        val versions = obj.asMap().mapNotNull { (key, value) ->
            if (key == "ideVersion") return@mapNotNull null
            val version = value.asSafeString ?: return@mapNotNull null
            key to version
        }.toMap()

        return DependencyVersionState(versions)
    }
}

@State(name = "KotlinWizardDependencyVersionStore", storages = [Storage("kotlin-wizard-data.xml")])
class DependencyVersionStore : IdeVersionedDataStorage<DependencyVersionState>(
    parser = DependencyVersionParser,
    defaultState = DEFAULT_DEPENDENCY_DATA
) {
    override fun newState(): DependencyVersionState = DependencyVersionState()

    companion object {
        @JvmStatic
        fun getInstance(): DependencyVersionStore {
            return service()
        }

        @JvmStatic
        fun getVersion(key: String): String? {
            val instance = getInstance()
            return instance.state?.getVersion(key) ?: DEFAULT_DEPENDENCY_DATA.getVersion(key)
        }
    }
}

internal fun DependencyVersionState.generateDefaultData(): String {
    val sortedDependencies = versions.toList().sortedBy { it.first }
    return """
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.tools.projectWizard.compatibility;

import org.jetbrains.kotlin.tools.projectWizard.compatibility.DependencyVersionState

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Kotlin Wizard Default Data" configuration instead
 */
internal val DEFAULT_DEPENDENCY_DATA = DependencyVersionState(
    versions = mapOf(
${sortedDependencies.joinToString("," + System.lineSeparator()) {
    " ".repeat(8) + "\"${it.first}\" to \"${it.second}\""
}}
    )
)
""".trimIndent()
}