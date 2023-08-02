/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard

import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinWizardVersionState
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinWizardVersionStore
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

@Suppress("ClassName", "SpellCheckingInspection", "Unused")
object Versions {
    // Most of the versions in this file are not used anymore in favour of the Dependencies object
    // However, we are keeping these fields for API compatibility reasons

    private fun loadVersion(default: String, f: (KotlinWizardVersionState).() -> String?): Version {
        val version = KotlinWizardVersionStore.getInstance().state?.let(f) ?: default
        return Version.fromString(version)
    }

    val KOTLIN = loadVersion("1.8.21") { kotlinPluginVersion }
    val KOTLIN_FOR_COMPOSE = loadVersion("1.7.20") { kotlinForComposeVersion }
    val COMPOSE_COMPILER_EXTENSION = loadVersion("1.4.3") { composeCompilerExtension }
    val GRADLE = Version.fromString("8.1.1")
    val JUNIT = Dependencies.JUNIT.version
    val JUNIT5 = Dependencies.JUNIT5.version

    object ANDROID {
        val ANDROID_MATERIAL = Dependencies.ANDROID.MATERIAL.version
        val ANDROIDX_APPCOMPAT = Dependencies.ANDROID.APP_COMPAT.version
        val ANDROIDX_CONSTRAINTLAYOUT = Dependencies.ANDROID.CONSTRAINT_LAYOUT.version
        val ANDROIDX_COMPOSE = Dependencies.ANDROID.COMPOSE_UI.version
        val ANDROIDX_ACTIVITY = Dependencies.ANDROID.ACTIVITY.version
    }

    object KOTLINX {
        val KOTLINX_HTML = Dependencies.KOTLINX.KOTLINX_HTML.version
        val KOTLINX_NODEJS = Dependencies.KOTLINX.KOTLINX_NODEJS.version
    }

    object JS_WRAPPERS {
        val KOTLIN_REACT = Dependencies.JS_WRAPPERS.KOTLIN_REACT.version
        val KOTLIN_REACT_DOM = Dependencies.JS_WRAPPERS.KOTLIN_REACT_DOM.version
        val KOTLIN_EMOTION = Dependencies.JS_WRAPPERS.KOTLIN_EMOTION.version
        val KOTLIN_REACT_ROUTER_DOM = Dependencies.JS_WRAPPERS.KOTLIN_REACT_ROUTER_DOM.version
        val KOTLIN_REDUX = Dependencies.JS_WRAPPERS.KOTLIN_REDUX.version
        val KOTLIN_REACT_REDUX = Dependencies.JS_WRAPPERS.KOTLIN_REACT_REDUX.version
    }

    object GRADLE_PLUGINS {
        val ANDROID = loadVersion("8.1.0") { gradleAndroidVersion }

        val MIN_GRADLE_FOOJAY_VERSION = loadVersion("7.6") { minGradleFoojayVersion }
        val FOOJAY_VERSION = loadVersion("0.5.0") { foojayVersion }
    }

    object MAVEN_PLUGINS {
        val SUREFIRE = loadVersion("2.22.2") { surefireVersion }
        val FAILSAFE = loadVersion("2.22.2") { failsafeVersion }
    }
}