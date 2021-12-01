/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard

import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

@Suppress("ClassName", "SpellCheckingInspection")
object Versions {
    val KOTLIN = version("1.5.0") // used as fallback version
    val GRADLE = version("7.0.2")
    val KTOR = version("1.6.3")
    val JUNIT = version("4.13")
    val JUNIT5 = version("5.6.0")
    val JETBRAINS_COMPOSE = version("1.0.0")

    val KOTLIN_VERSION_FOR_COMPOSE = version("1.5.31")
    val GRADLE_VERSION_FOR_COMPOSE = version("6.9")

    object COMPOSE {
        val ANDROID_ACTIVITY_COMPOSE = version("1.3.0")
    }

    object ANDROID {
        val ANDROID_MATERIAL = version("1.2.1")
        val ANDROIDX_APPCOMPAT = version("1.2.0")
        val ANDROIDX_CONSTRAINTLAYOUT = version("2.0.2")
        val ANDROIDX_KTX = version("1.3.1")
    }

    object KOTLINX {
        val KOTLINX_HTML = version("0.7.2")
        val KOTLINX_NODEJS: Version = version("0.0.7")
    }

    object JS_WRAPPERS {
        val KOTLIN_REACT = wrapperVersion("17.0.2")
        val KOTLIN_REACT_DOM = KOTLIN_REACT
        val KOTLIN_STYLED = wrapperVersion("5.3.1")
        val KOTLIN_REACT_ROUTER_DOM = wrapperVersion("5.2.0")
        val KOTLIN_REDUX = wrapperVersion("4.1.0")
        val KOTLIN_REACT_REDUX = wrapperVersion("7.2.4")

        private fun wrapperVersion(version: String): Version =
            version("$version-pre.240-kotlin-1.5.30")
    }

    object GRADLE_PLUGINS {
        val ANDROID = version("4.1.2")
    }

    object MAVEN_PLUGINS {
        val SUREFIRE = version("2.22.2")
        val FAILSAFE = SUREFIRE
    }
}

private fun version(version: String) = Version.fromString(version)
