/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard

import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

@Suppress("ClassName", "SpellCheckingInspection")
object Versions {
    val KOTLIN = version("1.5.0") // used as fallback version
    val KOTLIN_FOR_COMPOSE = version("1.7.20")
    val COMPOSE_COMPILER_EXTENSION = version("1.3.2")
    val GRADLE = version("7.5")
    val KTOR = version("2.0.2")
    val JUNIT = version("4.13.2")
    val JUNIT5 = version("5.8.2")

    object ANDROID {
        val ANDROID_MATERIAL = version("1.5.0")
        val ANDROIDX_APPCOMPAT = version("1.4.1")
        val ANDROIDX_CONSTRAINTLAYOUT = version("2.1.3")
        val ANDROIDX_COMPOSE = version("1.2.1")
        val ANDROIDX_ACTIVITY = version("1.5.1")
    }

    object KOTLINX {
        val KOTLINX_HTML = version("0.7.2")
        val KOTLINX_NODEJS: Version = version("0.0.7")
    }

    object JS_WRAPPERS {
        val KOTLIN_REACT = wrapperVersion("18.2.0")
        val KOTLIN_REACT_DOM = KOTLIN_REACT
        val KOTLIN_EMOTION = wrapperVersion("11.9.3")
        val KOTLIN_REACT_ROUTER_DOM = wrapperVersion("6.3.0")
        val KOTLIN_REDUX = wrapperVersion("4.1.2")
        val KOTLIN_REACT_REDUX = wrapperVersion("7.2.6")

        private fun wrapperVersion(version: String): Version =
            version("$version-pre.346")
    }

    object GRADLE_PLUGINS {
        val ANDROID = version("7.3.1")
    }

    object MAVEN_PLUGINS {
        val SUREFIRE = version("2.22.2")
        val FAILSAFE = SUREFIRE
    }
}

private fun version(version: String) = Version.fromString(version)
