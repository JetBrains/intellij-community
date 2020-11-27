/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.util.delegatorpatcher

import java.io.File
import java.util.*

fun getDelegators(projectRoot: File, mode: Mode): List<Delegator> {
    // Don't try to depend on module with stdlib because otherwise it won't be possible to compile
    // the project locally in cooperative compilation mode. Despite on the fact, that stdlib is Gradle
    // module, JPS still tries to process it somehow, and it detects unresolved references in stdlib
    // (probably because JPS treats kotlin-stdlib modules as "Kotlin not configured" modules). And
    // because of that compilation fails. That's why we use fixated version of stdlib from maven here
    // and set 'forceMvnModeOnly = true'
    val version = when (mode) {
        Mode.MVN -> null
        Mode.SRC -> Properties().apply { load(projectRoot.resolve("kotlinc/gradle.properties").inputStream()) }
            .getProperty("bootstrap.kotlin.default.version")
    }
    val stdlibCommon = MavenArtifact("org.jetbrains.kotlin", "kotlin-stdlib-common-for-ide", fixatedVersion = version)

    return listOf(
        Delegator("allopen-compiler-plugin"),
        Delegator("android-extensions-compiler-plugin", libraryLevel = LibraryLevel.PROJECT),
        Delegator("compiler-components-for-jps"),
        Delegator("kotlin-dist", libraryLevel = LibraryLevel.PROJECT),
        Delegator("incremental-compilation-impl-tests", kotlincTestModules = listOf("kotlin.compiler.incremental-compilation-impl.test")),
        Delegator("kotlin-build-common-tests", kotlincTestModules = listOf("kotlin.kotlin-build-common.test")),
        Delegator("kotlin-compiler"),
        Delegator("kotlin-gradle-statistics"),
        Delegator("kotlin-reflect", libraryLevel = LibraryLevel.PROJECT),
        Delegator("kotlin-scripting-common"),
        Delegator("kotlin-scripting-compiler"),
        Delegator("kotlin-scripting-compiler-impl"),
        Delegator("kotlin-scripting-jvm"),
        Delegator("kotlin-script-runtime"),
        Delegator("kotlin-script-util"),
        Delegator(
            "kotlin-stdlib-jdk8",
            mavenArtifact = MavenArtifact(
                "org.jetbrains.kotlin", "kotlin-stdlib-jdk8-for-ide", fixatedVersion = version, dependencies = listOf(
                    MavenArtifact("org.jetbrains.kotlin", "kotlin-stdlib-for-ide", fixatedVersion = version),
                    stdlibCommon,
                    MavenArtifact("org.jetbrains", "annotations", fixatedVersion = "13.0"),
                    MavenArtifact("org.jetbrains.kotlin", "kotlin-stdlib-jdk7-for-ide", fixatedVersion = version),
                )
            ),
            libraryLevel = LibraryLevel.PROJECT,
            forceMvnModeOnly = true
        ),
        Delegator("kotlin-stdlib-common", mavenArtifact = stdlibCommon, libraryLevel = LibraryLevel.PROJECT, forceMvnModeOnly = true),
        Delegator("kotlin-coroutines-experimental-compat"),
        Delegator("kotlinx-serialization-compiler-plugin"),
        Delegator("noarg-compiler-plugin"),
        Delegator("parcelize-compiler-plugin"),
        Delegator("sam-with-receiver-compiler-plugin"),
    )
}
