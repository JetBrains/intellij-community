// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.google.gson.JsonObject
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gradle.jvmcompat.*

internal object KotlinWizardVersionParser : IdeVersionedDataParser<KotlinWizardVersionState>() {
    override fun parseJson(data: JsonObject): KotlinWizardVersionState? {
        val obj = data.asSafeJsonObject ?: return null

        val versionData = KotlinWizardVersionState()

        versionData.kotlinPluginVersion = obj["kotlinVersion"]?.asSafeString ?: return null
        versionData.kotlinForComposeVersion = obj["kotlinForComposeVersion"]?.asSafeString ?: return null
        versionData.composeCompilerExtension = obj["composeCompilerExtension"]?.asSafeString ?: return null
        versionData.minGradleFoojayVersion = obj["minGradleFoojayVersion"]?.asSafeString ?: return null
        versionData.minKotlinFoojayVersion = obj["minKotlinFoojayVersion"]?.asSafeString ?: return null
        versionData.gradleAndroidVersion = obj["gradleAndroidVersion"]?.asSafeString ?: return null
        versionData.foojayVersion = obj["foojayVersion"]?.asSafeString ?: return null
        versionData.failsafeVersion = obj["failsafeVersion"]?.asSafeString ?: return null
        versionData.surefireVersion = obj["surefireVersion"]?.asSafeString ?: return null
        versionData.codehausMojoExecVersion = obj["codehausMojoExecVersion"]?.asSafeString ?: return null

        return versionData
    }
}

class KotlinWizardVersionState() : IdeVersionedDataState() {
    constructor(
        kotlinPluginVersion: String,
        kotlinForComposeVersion: String,
        composeCompilerExtension: String,
        minGradleFoojayVersion: String,
        minKotlinFoojayVersion: String,
        foojayVersion: String,
        failsafeVersion: String,
        surefireVersion: String,
        gradleAndroidVersion: String,
        codehausMojoExecVersion: String
    ) : this() {
        this.kotlinPluginVersion = kotlinPluginVersion
        this.kotlinForComposeVersion = kotlinForComposeVersion
        this.composeCompilerExtension = composeCompilerExtension
        this.minGradleFoojayVersion = minGradleFoojayVersion
        this.minKotlinFoojayVersion = minKotlinFoojayVersion
        this.foojayVersion = foojayVersion
        this.failsafeVersion = failsafeVersion
        this.surefireVersion = surefireVersion
        this.gradleAndroidVersion = gradleAndroidVersion
        this.codehausMojoExecVersion = codehausMojoExecVersion
    }


    var kotlinPluginVersion by string()
    var kotlinForComposeVersion by string()
    var composeCompilerExtension by string()
    var minGradleFoojayVersion by string()
    var minKotlinFoojayVersion by string()
    var foojayVersion by string()
    var failsafeVersion by string()
    var surefireVersion by string()
    var gradleAndroidVersion by string()
    var codehausMojoExecVersion by string()
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
    minKotlinFoojayVersion = "$minKotlinFoojayVersion",
    foojayVersion = "$foojayVersion",
    failsafeVersion = "$failsafeVersion",
    surefireVersion = "$surefireVersion",
    gradleAndroidVersion = "$gradleAndroidVersion",
    codehausMojoExecVersion = "$codehausMojoExecVersion"
)
""".trimIndent()
}