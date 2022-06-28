// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.*

const val ktGroup = "org.jetbrains.kotlin"

fun generateKotlincLibraries(
    kotlincArtifactsMode: KotlincArtifactsMode,
    kotlincVersion: String,
    jpsPluginVersion: String,
    isCommunity: Boolean,
): List<JpsLibrary> {
    @Suppress("NAME_SHADOWING") val jpsPluginVersion = jpsPluginVersion.takeUnless { it == "dev" } ?: kotlincVersion
    return listOfNotNull(
        kotlincForIdeWithStandardNaming("kotlinc.allopen-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.android-extensions-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fir-tests", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fir", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-impl-base", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-impl-base-tests", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-providers", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.analysis-project-structure", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.symbol-light-classes", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.incremental-compilation-impl-tests", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-backend-native", kotlincVersion).takeUnless { isCommunity },
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-build-common-tests", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-cli", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-tests", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-common", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fe10", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fir", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-ir", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-gradle-statistics", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-stdlib-minimal-for-test", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlinx-serialization-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.lombok-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.low-level-api-fir", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.noarg-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.parcelize-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.sam-with-receiver-compiler-plugin", kotlincVersion),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-common", kotlincVersion),

        kotlincWithStandardNaming("kotlinc.kotlin-scripting-common", kotlincVersion),
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-compiler-impl", kotlincVersion),
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-jvm", kotlincVersion),
        kotlincWithStandardNaming("kotlinc.kotlin-script-runtime", kotlincVersion, transitive = true),

        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-plugin-tests", jpsPluginVersion),
        kotlincWithStandardNaming("kotlinc.kotlin-dist", jpsPluginVersion, postfix = "-for-ide"),
        kotlincWithStandardNaming("kotlinc.kotlin-jps-plugin-classpath", jpsPluginVersion),

        kotlincWithStandardNaming(
            "kotlinc.kotlin-reflect",
            kotlincVersion,
            transitive = true,
            excludes = listOf(MavenId(ktGroup, "kotlin-stdlib"))
        ),

        run {
            val mavenIds = listOf(
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-jdk8:$kotlincVersion"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib:$kotlincVersion"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-common:$kotlincVersion"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-jdk7:$kotlincVersion")
            )

            JpsLibrary(
                "kotlinc.kotlin-stdlib",
                JpsLibrary.Kind.Maven(mavenIds.first(), excludes = listOf(MavenId("org.jetbrains", "annotations"))),
                annotations = listOf(JpsUrl.File(JpsPath.ProjectDir("lib/annotations/kotlin", isCommunity))),
                classes = mavenIds.map { JpsUrl.Jar(JpsPath.MavenRepository(it)) },
                sources = mavenIds.map { JpsUrl.Jar(JpsPath.MavenRepository(it, isSources = true)) }
            )
        },
    ).map { jpsLibrary ->
        when (kotlincArtifactsMode) {
            KotlincArtifactsMode.MAVEN -> jpsLibrary
            KotlincArtifactsMode.BOOTSTRAP -> jpsLibrary.copy(
                kind = JpsLibrary.Kind.Jars,
                classes = jpsLibrary.classes.map { it.convertKotlincMvnToBootstrap(isCommunity) },
                sources = jpsLibrary.sources.map { it.convertKotlincMvnToBootstrap(isCommunity) },
            )
        }
    }
}

private fun JpsUrl.convertKotlincMvnToBootstrap(isCommunity: Boolean): JpsUrl {
    val jpsPath = this.jpsPath
    require(jpsPath is JpsPath.MavenRepository)
    return JpsUrl.Jar(JpsPath.ProjectDir("../build/repo/${jpsPath.path}", isCommunity))
}

private fun kotlincForIdeWithStandardNaming(name: String, version: String, includeSources: Boolean = true): JpsLibrary {
    return kotlincWithStandardNaming(name, version, includeSources, "-for-ide")
}

private fun kotlincWithStandardNaming(
    name: String,
    version: String,
    includeSources: Boolean = true,
    postfix: String = "",
    transitive: Boolean = false,
    excludes: List<MavenId> = emptyList(),
): JpsLibrary {
    require(name.startsWith("kotlinc."))
    return singleJarMvnLib(
        name = name,
        mavenCoordinates = "$ktGroup:${name.removePrefix("kotlinc.")}$postfix:$version",
        transitive = transitive,
        includeSources = includeSources,
        excludes = excludes,
    )
}
