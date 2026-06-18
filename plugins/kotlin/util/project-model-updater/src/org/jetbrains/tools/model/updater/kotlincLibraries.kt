// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.GeneratorPreferences.ArtifactMode
import org.jetbrains.tools.model.updater.impl.JpsLibrary
import org.jetbrains.tools.model.updater.impl.JpsPath
import org.jetbrains.tools.model.updater.impl.JpsRemoteRepository
import org.jetbrains.tools.model.updater.impl.JpsUrl
import org.jetbrains.tools.model.updater.impl.MavenId

private const val ktGroup = "org.jetbrains.kotlin"

/** The version should be aligned with gradle.properties#defaultSnapshotVersion from the Kotlin repo */
internal const val BOOTSTRAP_VERSION = "2.4.255-dev-255"

// see .idea/jarRepositories.xml
// This is the new repository where artifacts SINCE `2.2.20-dev-2414` are published to.
private val INTELLIJ_DEPENDENCIES_REPOSITORY = JpsRemoteRepository(
    "intellij-dependencies",
    "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies",
)

private val INTELLIJ_EXPERIMENTAL_DEPENDENCIES_REPOSITORY = JpsRemoteRepository(
    "kotlin-experimental",
    "https://packages.jetbrains.team/maven/p/kt/experimental",
)

internal class ArtifactCoordinates(private val originalVersion: String, val mode: ArtifactMode, val repository: JpsRemoteRepository) {
    val version: String
        get() = when (mode) {
            ArtifactMode.MAVEN, ArtifactMode.MAVEN_EXPERIMENTAL -> originalVersion
            ArtifactMode.BOOTSTRAP -> BOOTSTRAP_VERSION
        }
}

internal val GeneratorPreferences.kotlincArtifactCoordinates: ArtifactCoordinates
    get() = ArtifactCoordinates(kotlincVersion, kotlincArtifactsMode, kotlincRepository ?: INTELLIJ_DEPENDENCIES_REPOSITORY)

private val GeneratorPreferences.kotlincRepository: JpsRemoteRepository?
    get() = when (kotlincArtifactsMode) {
        ArtifactMode.MAVEN_EXPERIMENTAL -> INTELLIJ_EXPERIMENTAL_DEPENDENCIES_REPOSITORY
        ArtifactMode.MAVEN -> INTELLIJ_DEPENDENCIES_REPOSITORY
        ArtifactMode.BOOTSTRAP -> null
    }

internal val GeneratorPreferences.jpsArtifactCoordinates: ArtifactCoordinates
    get() = if (jpsPluginVersion != "dev") {
        ArtifactCoordinates(jpsPluginVersion, jpsPluginArtifactsMode, INTELLIJ_DEPENDENCIES_REPOSITORY)
    } else {
        kotlincArtifactCoordinates
    }

internal fun generateKotlincLibraries(preferences: GeneratorPreferences, isCommunity: Boolean): List<JpsLibrary> {
    val kotlincCoordinates = preferences.kotlincArtifactCoordinates
    val jpsPluginCoordinates = preferences.jpsArtifactCoordinates

    return buildLibraryList(isCommunity) {
        kotlincForIdeWithStandardNaming("kotlinc.allopen-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-k2", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-fe10", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-impl-base", kotlincCoordinates)
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
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-dataframe-compiler-plugin", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-common", kotlincCoordinates)

        kotlincForIdeWithStandardNaming("kotlinc.kotlin-objcexport-header-generator", kotlincCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-swift-export", kotlincCoordinates)

        kotlincWithStandardNaming("kotlinc.kotlin-scripting-common", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-dependencies", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-compiler-impl", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-jvm", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-script-runtime", kotlincCoordinates, transitive = true)

        kotlincWithStandardNaming("kotlinc.kotlin-build-tools-api", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-build-tools-impl", kotlincCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-build-tools-cri-impl", kotlincCoordinates)

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
    jpsLibraryName: String = name,
) {
    require(name.startsWith("kotlinc."))
    val jpsLibrary = singleJarMavenLibrary(
        jpsLibraryName = jpsLibraryName,
        mavenCoordinates = "$ktGroup:${name.removePrefix("kotlinc.")}$postfix:${coordinates.version}",
        transitive = transitive,
        includeSources = includeSources,
        excludes = excludes,
        repository = coordinates.repository
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
            is JpsPath.MavenRepository -> {
                val snapshotDirectoryPath = KotlinTestsDependenciesUtil.kotlinCompilerSnapshotLocationInsideCommunity
                JpsUrl.Jar(JpsPath.ProjectDir("$snapshotDirectoryPath/${url.path.relativePath}", isCommunity))
            }
        }
    }

    return when (artifactsMode) {
        ArtifactMode.MAVEN, ArtifactMode.MAVEN_EXPERIMENTAL -> this
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
