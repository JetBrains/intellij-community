// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.GeneratorPreferences.ArtifactMode
import org.jetbrains.tools.model.updater.impl.JpsLibrary
import org.jetbrains.tools.model.updater.impl.JpsPath
import org.jetbrains.tools.model.updater.impl.JpsUrl
import org.jetbrains.tools.model.updater.impl.MavenId

private const val ktGroup = "org.jetbrains.kotlin"
private const val BOOTSTRAP_VERSION = "1.9.255"

private class ArtifactCoordinates(private val originalVersion: String, val mode: ArtifactMode) {
    val version: String
        get() = when (mode) {
            ArtifactMode.MAVEN -> originalVersion
            ArtifactMode.BOOTSTRAP -> BOOTSTRAP_VERSION
        }
}

private val GeneratorPreferences.kotlincArtifactCoordinates: ArtifactCoordinates
    get() = ArtifactCoordinates(kotlincVersion, kotlincArtifactsMode)

private val GeneratorPreferences.nativeArtifactCoordinates: ArtifactCoordinates
    get() = when (kotlincArtifactsMode) {
        ArtifactMode.MAVEN -> kotlincArtifactCoordinates
        ArtifactMode.BOOTSTRAP ->
            if (bootstrapWithNative)
                kotlincArtifactCoordinates
            else
                ArtifactCoordinates(originalVersion = kotlincVersion, mode = ArtifactMode.MAVEN)
    }

private val GeneratorPreferences.jpsArtifactCoordinates: ArtifactCoordinates
    get() = ArtifactCoordinates(jpsPluginVersion, jpsPluginArtifactsMode)

internal fun generateKotlincLibraries(preferences: GeneratorPreferences, isCommunity: Boolean): List<JpsLibrary> {
    val kotlincCoordinates = preferences.kotlincArtifactCoordinates
    val jpsPluginCoordinates = preferences.jpsArtifactCoordinates.takeIf { it.version != "dev" } ?: kotlincCoordinates

    return buildLibraryList(isCommunity) {
        kotlincForIdeWithStandardNaming("kotlinc.allopen-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.android-extensions-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fir-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fir", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fe10", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-impl-base", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-impl-base-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-providers", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-project-structure", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.symbol-light-classes", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.incremental-compilation-impl-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-build-common-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-cli", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-common", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fe10", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fir", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-ir", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-gradle-statistics", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-stdlib-minimal-for-test", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlinx-serialization-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.lombok-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.low-level-api-fir", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.noarg-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.parcelize-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.sam-with-receiver-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.assignment-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-common", kotlincCoordinates)

        if (!isCommunity) {
            kotlincForIdeWithStandardNaming("kotlinc.kotlin-backend-native", preferences.nativeArtifactCoordinates)
        }

        kotlincWithStandardNaming("kotlinc.kotlin-scripting-common", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-compiler-impl", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-jvm", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-script-runtime", kotlincCoordinates, transitive = true)

        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-plugin-tests", jpsPluginCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-dist", jpsPluginCoordinates, postfix = "-for-ide")
        kotlincWithStandardNaming("kotlinc.kotlin-jps-plugin-classpath", jpsPluginCoordinates)

        run {
            val mavenIds = listOf(
                MavenId.parse("$ktGroup:kotlin-stdlib-jdk8:${kotlincCoordinates.version}"),
                MavenId.parse("$ktGroup:kotlin-stdlib:${kotlincCoordinates.version}"),
                MavenId.parse("$ktGroup:kotlin-stdlib-common:${kotlincCoordinates.version}"),
                MavenId.parse("$ktGroup:kotlin-stdlib-jdk7:${kotlincCoordinates.version}")
            )

            val annotationLibrary = JpsLibrary(
                "kotlinc.kotlin-stdlib",
                JpsLibrary.LibraryType.Repository(mavenIds.first(), excludes = listOf(MavenId("org.jetbrains", "annotations"))),
                annotations = listOf(JpsUrl.File(JpsPath.ProjectDir("lib/annotations/kotlin", isCommunity))),
                classes = mavenIds.map { JpsUrl.Jar(JpsPath.MavenRepository(it)) },
                sources = mavenIds.map { JpsUrl.Jar(JpsPath.MavenRepository(it, "sources")) }
            )

            addLibrary(annotationLibrary.convertMavenUrlToCooperativeIfNeeded(kotlincCoordinates.mode, isCommunity))
        }
    }
}

private class LibraryListBuilder(val isCommunity: Boolean) {
    private val libraries = mutableListOf<JpsLibrary>()

    fun addLibrary(library: JpsLibrary) {
        libraries += library
    }

    fun build(): List<JpsLibrary> = libraries.toList()
}

private fun buildLibraryList(isCommunity: Boolean, builder: LibraryListBuilder.() -> Unit): List<JpsLibrary> =
    LibraryListBuilder(isCommunity).apply(builder).build()

private fun LibraryListBuilder.kotlincForIdeWithStandardNaming(
    name: String,
    coordinates: ArtifactCoordinates,
    includeSources: Boolean = true
) {
    kotlincWithStandardNaming(name, coordinates, includeSources, "-for-ide")
}

private fun LibraryListBuilder.kotlincWithStandardNaming(
    name: String,
    coordinates: ArtifactCoordinates,
    includeSources: Boolean = true,
    postfix: String = "",
    transitive: Boolean = false,
    excludes: List<MavenId> = emptyList(),
) {
    require(name.startsWith("kotlinc."))
    val jpsLibrary = singleJarMavenLibrary(
        name = name,
        mavenCoordinates = "$ktGroup:${name.removePrefix("kotlinc.")}$postfix:${coordinates.version}",
        transitive = transitive,
        includeSources = includeSources,
        excludes = excludes,
    )
    addLibrary(jpsLibrary.convertMavenUrlToCooperativeIfNeeded(coordinates.mode, isCommunity))
}

private fun singleJarMavenLibrary(
    name: String,
    mavenCoordinates: String,
    excludes: List<MavenId> = emptyList(),
    transitive: Boolean = true,
    includeSources: Boolean = true,
): JpsLibrary {
    val mavenId = MavenId.parse(mavenCoordinates)
    return JpsLibrary(
        name,
        JpsLibrary.LibraryType.Repository(mavenId, includeTransitive = transitive, excludes = excludes),
        classes = listOf(JpsUrl.Jar(JpsPath.MavenRepository(mavenId))),
        sources = listOf(JpsUrl.Jar(JpsPath.MavenRepository(mavenId, classifier = "sources"))).takeIf { includeSources } ?: emptyList()
    )
}

private fun JpsLibrary.convertMavenUrlToCooperativeIfNeeded(artifactsMode: ArtifactMode, isCommunity: Boolean): JpsLibrary {
    fun convertUrl(url: JpsUrl): JpsUrl {
        return when (url.path) {
            is JpsPath.ProjectDir -> url
            is JpsPath.MavenRepository -> JpsUrl.Jar(JpsPath.ProjectDir("../build/repo/${url.path.relativePath}", isCommunity))
        }
    }

    return when (artifactsMode) {
        ArtifactMode.MAVEN -> this
        ArtifactMode.BOOTSTRAP -> JpsLibrary(
            name = name,
            type = JpsLibrary.LibraryType.Plain,
            annotations = annotations.map(::convertUrl),
            classes = classes.map(::convertUrl),
            javadoc = javadoc.map(::convertUrl),
            sources = sources.map(::convertUrl)
        )
    }
}