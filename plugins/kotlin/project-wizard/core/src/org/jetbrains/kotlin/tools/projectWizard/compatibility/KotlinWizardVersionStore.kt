// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.google.gson.JsonObject
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataState
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataStorage

internal object KotlinWizardVersionParser : IdeVersionedDataParser<KotlinWizardVersionState>() {
    override fun parseJson(data: JsonObject): KotlinWizardVersionState? {
        val obj = data.takeIf { it.isJsonObject }?.asJsonObject ?: return null

        val versionData = KotlinWizardVersionState()

        versionData.kotlinPluginVersion = obj["kotlinVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.kotlinForComposeVersion = obj["kotlinForComposeVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.composeCompilerExtension = obj["composeCompilerExtension"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.minGradleFoojayVersion = obj["minGradleFoojayVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.gradleAndroidVersion = obj["gradleAndroidVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.foojayVersion = obj["foojayVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.failsafeVersion = obj["failsafeVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        versionData.surefireVersion = obj["surefireVersion"]?.takeIf { it.isJsonPrimitive }?.asString ?: return null

        return versionData
    }
}

class KotlinWizardVersionState() : IdeVersionedDataState() {
    constructor(
        kotlinPluginVersion: String,
        kotlinForComposeVersion: String,
        composeCompilerExtension: String,
        minGradleFoojayVersion: String,
        foojayVersion: String,
        failsafeVersion: String,
        surefireVersion: String,
        gradleAndroidVersion: String
    ) : this() {
        this.kotlinPluginVersion = kotlinPluginVersion
        this.kotlinForComposeVersion = kotlinForComposeVersion
        this.composeCompilerExtension = composeCompilerExtension
        this.minGradleFoojayVersion = minGradleFoojayVersion
        this.foojayVersion = foojayVersion
        this.failsafeVersion = failsafeVersion
        this.surefireVersion = surefireVersion
        this.gradleAndroidVersion = gradleAndroidVersion
    }


    var kotlinPluginVersion by string()
    var kotlinForComposeVersion by string()
    var composeCompilerExtension by string()
    var minGradleFoojayVersion by string()
    var foojayVersion by string()
    var failsafeVersion by string()
    var surefireVersion by string()
    var gradleAndroidVersion by string()
}

@State(name = "KotlinWizardVersionStore", storages = [Storage("kotlin-wizard-data.xml")])
class KotlinWizardVersionStore : IdeVersionedDataStorage<KotlinWizardVersionState>(
    parser = KotlinWizardVersionParser,
    defaultState = DEFAULT_KOTLIN_WIZARD_VERSIONS
) {
    override fun newState(): KotlinWizardVersionState = KotlinWizardVersionState()

    companion object {
        @JvmStatic
        fun getInstance(): KotlinWizardVersionStore {
            return service()
        }
    }
}


internal fun KotlinWizardVersionState.generateDefaultData(): String {
    return """
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.tools.projectWizard.compatibility;

import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinWizardVersionState

/**
 * NOTE THIS FILE IS AUTO-GENERATED
 * DO NOT EDIT IT BY HAND, run "Generate Kotlin Wizard Default Data" configuration instead
 */
internal val DEFAULT_KOTLIN_WIZARD_VERSIONS = KotlinWizardVersionState(
    kotlinPluginVersion = "$kotlinPluginVersion",
    kotlinForComposeVersion = "$kotlinForComposeVersion",
    composeCompilerExtension = "$composeCompilerExtension",
    minGradleFoojayVersion = "$minGradleFoojayVersion",
    foojayVersion = "$foojayVersion",
    failsafeVersion = "$failsafeVersion",
    surefireVersion = "$surefireVersion",
    gradleAndroidVersion = "$gradleAndroidVersion"
)
""".trimIndent()
}