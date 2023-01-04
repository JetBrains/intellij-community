/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.tools.projectWizard

import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version

@Suppress("ClassName", "SpellCheckingInspection")
object Versions {
    val KOTLIN = version("1.5.0") // used as fallback version
    val GRADLE = version("7.5.1")
    val KTOR = version("2.2.1")
    val JUNIT = version("4.13.2")
    val JUNIT5 = version("5.9.1")

    object ANDROID {
        val ANDROID_MATERIAL = version("1.7.0")
        val ANDROIDX_APPCOMPAT = version("1.5.1")
        val ANDROIDX_CONSTRAINTLAYOUT = version("2.1.4")
    }

    object KOTLINX {
        val KOTLINX_HTML = version("0.8.1")
        val KOTLINX_NODEJS: Version = version("0.0.7")
    }

    object JS_WRAPPERS {
        val KOTLIN_REACT = wrapperVersion("18.2.0")
        val KOTLIN_REACT_DOM = KOTLIN_REACT
        val KOTLIN_EMOTION = wrapperVersion("11.10.5")
        val KOTLIN_REACT_ROUTER_DOM = wrapperVersion("6.3.0")
        val KOTLIN_REDUX = wrapperVersion("4.1.2")
        val KOTLIN_REACT_REDUX = wrapperVersion("7.2.6")

        private fun wrapperVersion(version: String): Version =
            version("$version-pre.467")
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
