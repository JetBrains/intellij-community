// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.GeneratorPreferences.ArtifactMode
import org.jetbrains.tools.model.updater.impl.*

private const val ktGroup = "org.jetbrains.kotlin"
private const val BOOTSTRAP_VERSION = "2.1.255-dev-255"

// see .idea/jarRepositories.xml
private val KOTLIN_IDE_DEPS_REPOSITORY = JpsRemoteRepository(
    "kotlin-ide-plugin-deps",
    "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies"
)

private class ArtifactCoordinates(private val originalVersion: String, val mode: ArtifactMode) {
    val version: String
        get() = when (mode) {
            ArtifactMode.MAVEN -> originalVersion
            ArtifactMode.BOOTSTRAP -> BOOTSTRAP_VERSION
        }
}

private val GeneratorPreferences.kotlincArtifactCoordinates: ArtifactCoordinates
    get() = ArtifactCoordinates(kotlincVersion, kotlincArtifactsMode)

private val GeneratorPreferences.jpsArtifactCoordinates: ArtifactCoordinates
    get() = ArtifactCoordinates(jpsPluginVersion, jpsPluginArtifactsMode)

internal fun generateKotlincLibraries(preferences: GeneratorPreferences, isCommunity: Boolean): List<JpsLibrary> {
    val kotlincCoordinates = preferences.kotlincArtifactCoordinates
    val jpsPluginCoordinates = preferences.jpsArtifactCoordinates.takeIf { it.version != "dev" } ?: kotlincCoordinates

    return buildLibraryList(isCommunity) {
        kotlincForIdeWithStandardNaming("kotlinc.allopen-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.android-extensions-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-k2-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-k2", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-fe10", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-impl-base", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-impl-base-tests", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-platform-interface", kotlincCoordinates)
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
        kotlincForIdeWithStandardNaming("kotlinc.kotlinx-serialization-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.lombok-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.low-level-api-fir", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.noarg-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.parcelize-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.sam-with-receiver-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.assignment-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.scripting-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.compose-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.js-plain-objects-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-common", kotlincCoordinates)

        if (!isCommunity) {
            kotlincForIdeWithStandardNaming("kotlinc.kotlin-objcexport-header-generator", kotlincCoordinates)
        }

        kotlincWithStandardNaming("kotlinc.kotlin-scripting-common", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-dependencies", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-compiler-impl", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-jvm", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-script-runtime", kotlincCoordinates, transitive = true)

        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-plugin-tests", jpsPluginCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-dist", jpsPluginCoordinates, postfix = "-for-ide")
        kotlincWithStandardNaming("kotlinc.kotlin-jps-plugin-classpath", jpsPluginCoordinates)

        // bootstrap version of kotlin-jps-plugin-classpath required for testing
        kotlincWithStandardNaming(
            "kotlinc.kotlin-jps-plugin-classpath",
            kotlincCoordinates,
            jpsLibraryName = "kotlinc.kotlin-jps-plugin-classpath-bootstrap-for-tests",
        )
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
    repository: JpsRemoteRepository = KOTLIN_IDE_DEPS_REPOSITORY,
    jpsLibraryName: String = name,
) {
    require(name.startsWith("kotlinc."))
    val jpsLibrary = singleJarMavenLibrary(
        jpsLibraryName = jpsLibraryName,
        mavenCoordinates = "$ktGroup:${name.removePrefix("kotlinc.")}$postfix:${coordinates.version}",
        transitive = transitive,
        includeSources = includeSources,
        excludes = excludes,
        repository = repository
    )
    addLibrary(jpsLibrary.convertMavenUrlToCooperativeIfNeeded(coordinates.mode, isCommunity))
}

private fun singleJarMavenLibrary(
    jpsLibraryName: String,
    mavenCoordinates: String,
    excludes: List<MavenId> = emptyList(),
    transitive: Boolean = true,
    includeSources: Boolean = true,
    repository: JpsRemoteRepository,
): JpsLibrary {
    val mavenId = MavenId.parse(mavenCoordinates)
    return JpsLibrary(
        jpsLibraryName,
        JpsLibrary.LibraryType.Repository(mavenId, includeTransitive = transitive, excludes = excludes, remoteRepository = repository),
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