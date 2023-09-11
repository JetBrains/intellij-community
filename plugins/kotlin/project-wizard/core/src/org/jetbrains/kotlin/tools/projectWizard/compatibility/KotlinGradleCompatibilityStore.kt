// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.google.gson.JsonObject
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataState
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage
import org.jetbrains.plugins.gradle.util.Ranges

class KotlinGradleVersionMapping() : BaseState() {
    constructor(
        kotlin: String,
        gradle: String,
        comment: String? = null
    ) : this() {
        this.kotlin = kotlin
        this.gradle = gradle
        this.comment = comment
    }

    var kotlin by string()
    var gradle by string()
    var comment by string()
}

class KotlinGradleCompatibilityState() : IdeVersionedDataState() {
    constructor(kotlinVersions: List<String>, compatibility: List<KotlinGradleVersionMapping>) : this() {
        this.kotlinVersions.addAll(kotlinVersions)
        this.compatibility.addAll(compatibility)
    }

    var kotlinVersions by list<String>()
    var compatibility by list<KotlinGradleVersionMapping>()
}

internal object KotlinGradleCompatibilityParser : IdeVersionedDataParser<KotlinGradleCompatibilityState>() {
    override fun parseJson(data: JsonObject): KotlinGradleCompatibilityState? {
        val kotlinVersionsArr = data["kotlinVersions"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val kotlinVersions = kotlinVersionsArr.mapNotNull { entry ->
            val str = entry.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            IdeKotlinVersion.parse(str).getOrNull()?.toString()
        }

        val compatibilityArr = data["compatibility"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return null

        val compatibility = compatibilityArr.mapNotNull { entry ->
            val obj = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val kotlin = obj["kotlin"]?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val gradle = obj["gradle"]?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val comment = obj["comment"]?.takeIf { it.isJsonPrimitive }?.asString
            KotlinGradleVersionMapping(kotlin, gradle, comment)
        }

        return KotlinGradleCompatibilityState(kotlinVersions, compatibility)
    }
}

@State(name = "KotlinGradleCompatibilityStore", storages = [Storage("kotlin-wizard-data.xml")])
class KotlinGradleCompatibilityStore : IdeVersionedDataStorage<KotlinGradleCompatibilityState>(
    parser = KotlinGradleCompatibilityParser,
    defaultState = DEFAULT_KOTLIN_GRADLE_COMPATIBILITY_DATA
) {
    @Volatile
    private var supportedKotlinVersions: List<IdeKotlinVersion> = emptyList()

    @Volatile
    private var compatibility: List<Pair<Ranges<IdeKotlinVersion>, Ranges<GradleVersion>>> = emptyList()

    private fun applyState(state: KotlinGradleCompatibilityState) {
        compatibility = getCompatibilityRanges(state)
        supportedKotlinVersions = state.kotlinVersions.map(IdeKotlinVersion::get)
    }

    init {
        applyState(DEFAULT_KOTLIN_GRADLE_COMPATIBILITY_DATA)
    }

    override fun newState(): KotlinGradleCompatibilityState = KotlinGradleCompatibilityState()

    private fun getCompatibilityRanges(state: KotlinGradleCompatibilityState): List<Pair<Ranges<IdeKotlinVersion>, Ranges<GradleVersion>>> {
        return state.compatibility.map { entry ->
            val gradle = entry.gradle ?: ""
            val kotlin = entry.kotlin ?: ""
            val gradleRange = IdeVersionedDataParser.parseRange(gradle.split(','), GradleVersion::version)
            val kotlinRange = IdeVersionedDataParser.parseRange(kotlin.split(','), IdeKotlinVersion::get)
            kotlinRange to gradleRange
        }
    }

    override fun onStateChanged(newState: KotlinGradleCompatibilityState) {
        applyState(newState)
    }

    companion object {
        @JvmStatic
        fun getInstance(): KotlinGradleCompatibilityStore {
            return service()
        }

        fun allKotlinVersions(): List<IdeKotlinVersion> {
            return getInstance().state?.kotlinVersions?.map(IdeKotlinVersion::get) ?: emptyList()
        }

        fun kotlinVersionSupportsGradle(kotlinVersion: IdeKotlinVersion, gradleVersion: GradleVersion): Boolean {
            return getInstance().compatibility.any { (kotlinVersions, gradleVersions) ->
                kotlinVersion in kotlinVersions && gradleVersion in gradleVersions
            }
        }
    }
}

private fun KotlinGradleVersionMapping.toDefaultDataString(): String {
    return StringBuilder().apply {
        append(" ".repeat(8) + "KotlinGradleVersionMapping(")
        append(System.lineSeparator())
        append(" ".repeat(12) + "kotlin = \"$kotlin\",")
        append(System.lineSeparator())
        append(" ".repeat(12) + "gradle = \"$gradle\"")
        if (comment != null) {
            append(",")
        }
        append(System.lineSeparator())
        if (comment != null) {
            append(" ".repeat(12) + "comment = \"$comment\"")
            append(System.lineSeparator())
        }
        append(" ".repeat(8) + ")")
    }.toString()
}

internal fun KotlinGradleCompatibilityState.generateDefaultData(): String {
    return """
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.tools.projectWizard.compatibility;

import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinGradleCompatibilityState

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Kotlin Wizard Default Data" configuration instead
 */
internal val DEFAULT_KOTLIN_GRADLE_COMPATIBILITY_DATA = KotlinGradleCompatibilityState(
    kotlinVersions = listOf(
${kotlinVersions.joinToString("," + System.lineSeparator()) { " ".repeat(8) + "\"$it\"" }}
    ),
    compatibility = listOf(
${compatibility.toList().joinToString("," + System.lineSeparator()) { it.toDefaultDataString() }}
    )
)
""".trimIndent()
}