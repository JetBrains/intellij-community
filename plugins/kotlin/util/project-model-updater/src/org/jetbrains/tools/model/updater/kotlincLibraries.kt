// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.*

const val ktGroup = "org.jetbrains.kotlin"

fun generateKotlincLibraries(
    kotlincArtifactsCoordinates: ArtifactCoordinates,
    jpsPluginCoordinates: ArtifactCoordinates,
    isCommunity: Boolean,
): List<JpsLibrary> {
    val jpsPluginVersion = jpsPluginCoordinates.version.takeUnless { it == "dev" } ?: kotlincArtifactsCoordinates.version
    @Suppress("NAME_SHADOWING") val jpsPluginCoordinates = jpsPluginCoordinates.copy(version = jpsPluginVersion)

    return buildLibraryList(isCommunity) {
        kotlincForIdeWithStandardNaming("kotlinc.allopen-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.android-extensions-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.incremental-compilation-impl-tests", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-backend-native", kotlincArtifactsCoordinates).takeUnless { isCommunity }
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-build-common-tests", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-cli", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-tests", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-common", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fe10", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-ir", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-gradle-statistics", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-stdlib-minimal-for-test", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlinx-serialization-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.lombok-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.noarg-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.parcelize-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.sam-with-receiver-compiler-plugin", kotlincArtifactsCoordinates)
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-common", kotlincArtifactsCoordinates)

        kotlincWithStandardNaming("kotlinc.kotlin-scripting-common", kotlincArtifactsCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-compiler-impl", kotlincArtifactsCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-scripting-jvm", kotlincArtifactsCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-script-runtime", kotlincArtifactsCoordinates, transitive = true)

        kotlincForIdeWithStandardNaming("kotlinc.kotlin-jps-plugin-tests", jpsPluginCoordinates)
        kotlincWithStandardNaming("kotlinc.kotlin-dist", jpsPluginCoordinates, postfix = "-for-ide")
        kotlincWithStandardNaming("kotlinc.kotlin-jps-plugin-classpath", jpsPluginCoordinates)

        kotlincWithStandardNaming(
            "kotlinc.kotlin-reflect",
            kotlincArtifactsCoordinates,
            transitive = true,
            excludes = listOf(MavenId(ktGroup, "kotlin-stdlib"))
        )
        run {
            val mavenIds = listOf(
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-jdk8:${kotlincArtifactsCoordinates.version}"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib:${kotlincArtifactsCoordinates.version}"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-common:${kotlincArtifactsCoordinates.version}"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-jdk7:${kotlincArtifactsCoordinates.version}")
            )

            addLibrary(
                JpsLibrary(
                    "kotlinc.kotlin-stdlib",
                    JpsLibrary.Kind.Maven(mavenIds.first(), excludes = listOf(MavenId("org.jetbrains", "annotations"))),
                    annotations = listOf(JpsUrl.File(JpsPath.ProjectDir("lib/annotations/kotlin", isCommunity))),
                    classes = mavenIds.map { JpsUrl.Jar(JpsPath.MavenRepository(it)) },
                    sources = mavenIds.map { JpsUrl.Jar(JpsPath.MavenRepository(it, isSources = true)) },
                ).convertKotlincMvnToBootstrap(kotlincArtifactsCoordinates.mode, isCommunity)
            )
        }
    }
}

private fun JpsUrl.convertKotlincMvnToBootstrap(isCommunity: Boolean): JpsUrl {
    val jpsPath = this.jpsPath
    require(jpsPath is JpsPath.MavenRepository)
    return JpsUrl.Jar(JpsPath.ProjectDir("../build/repo/${jpsPath.path}", isCommunity))
}

private fun LibraryListBuilder.kotlincForIdeWithStandardNaming(
    name: String,
    artifactsCoordinates: ArtifactCoordinates,
    includeSources: Boolean = true
) {
    kotlincWithStandardNaming(name, artifactsCoordinates, includeSources, "-for-ide")
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

private fun LibraryListBuilder.kotlincWithStandardNaming(
    name: String,
    artifactsCoordinates: ArtifactCoordinates,
    includeSources: Boolean = true,
    postfix: String = "",
    transitive: Boolean = false,
    excludes: List<MavenId> = emptyList(),
) {
    require(name.startsWith("kotlinc."))
    val jpsLibrary = singleJarMvnLib(
        name = name,
        mavenCoordinates = "$ktGroup:${name.removePrefix("kotlinc.")}$postfix:${artifactsCoordinates.version}",
        transitive = transitive,
        includeSources = includeSources,
        excludes = excludes,
    )
    addLibrary(jpsLibrary.convertKotlincMvnToBootstrap(artifactsCoordinates.mode, isCommunity))
}

private fun JpsLibrary.convertKotlincMvnToBootstrap(artifactsMode: KotlincArtifactsMode, isCommunity: Boolean): JpsLibrary {
   return when (artifactsMode) {
        KotlincArtifactsMode.MAVEN -> this
        KotlincArtifactsMode.BOOTSTRAP -> copy(
            kind = JpsLibrary.Kind.Jars,
            classes = classes.map { it.convertKotlincMvnToBootstrap(isCommunity) },
            sources = sources.map { it.convertKotlincMvnToBootstrap(isCommunity) },
        )
    }
}
