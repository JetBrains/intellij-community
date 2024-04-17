// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility.libraries

import com.google.gson.JsonObject
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataState
import org.jetbrains.plugins.gradle.jvmcompat.asSafeJsonObject
import org.jetbrains.plugins.gradle.jvmcompat.asSafeString

class KotlinLibraryCompatibilityState() : IdeVersionedDataState() {
    constructor(groupId: String, artifactId: String, versions: Map<String, String>) : this() {
        this.artifactId = artifactId
        this.groupId = groupId
        this.versions.putAll(versions)
    }

    var artifactId: String? by string()
    var groupId: String? by string()
    var versions: MutableMap<String, String> by map()
}

internal object KotlinLibraryCompatibilityParser : IdeVersionedDataParser<KotlinLibraryCompatibilityState>() {
    override fun parseJson(data: JsonObject): KotlinLibraryCompatibilityState? {
        val obj = data.asSafeJsonObject ?: return null
        val artifactId = obj["artifactId"]?.asSafeString ?: return null
        val groupId = obj["groupId"]?.asSafeString ?: return null

        val versionsObj = obj["versions"].asSafeJsonObject ?: return null

        val versions = versionsObj.asMap().mapNotNull { (key, value) ->
            val version = value.asSafeString ?: return@mapNotNull null
            key to version
        }.toMap()

        return KotlinLibraryCompatibilityState(groupId, artifactId, versions)
    }
}

internal fun KotlinLibraryCompatibilityState.generateDefaultData(variableName: String): String {
    val versions = versions.toList().sortedBy { it.first }
    return """
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.tools.projectWizard.compatibility.libraries;

import org.jetbrains.kotlin.tools.projectWizard.compatibility.libraries.KotlinLibraryCompatibilityState

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Kotlin Wizard Default Data" configuration instead
 */
internal val $variableName = KotlinLibraryCompatibilityState(
    groupId = "$groupId",
    artifactId = "$artifactId",
    versions = mapOf(
${versions.joinToString("," + System.lineSeparator()) {
        " ".repeat(8) + "\"${it.first}\" to \"${it.second}\""
    }}
    )
)
""".trimIndent()
}