package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.*

const val ktGroup = "org.jetbrains.kotlin"

fun generateKotlincLibraries(kotlincArtifactsMode: KotlincArtifactsMode, version: String, isCommunity: Boolean): List<JpsLibrary> {
    return listOf(
        kotlincForIdeWithStandardNaming("kotlinc.allopen-compiler-plugin", version),
        kotlincForIdeWithStandardNaming("kotlinc.android-extensions-compiler-plugin", version),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fir-tests", version),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-fir", version),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api", version),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-impl-base", version),
        kotlincForIdeWithStandardNaming("kotlinc.high-level-api-impl-base-tests", version),
        kotlincForIdeWithStandardNaming("kotlinc.analysis-api-providers", version),
        kotlincForIdeWithStandardNaming("kotlinc.analysis-project-structure", version),
        kotlincForIdeWithStandardNaming("kotlinc.symbol-light-classes", version),
        kotlincForIdeWithStandardNaming("kotlinc.incremental-compilation-impl-tests", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-build-common-tests", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-cli", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-testdata", version, includeSources = false),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-tests", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-common", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fe10", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-fir", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-compiler-ir", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-dist", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-gradle-statistics", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlin-stdlib-minimal-for-test", version),
        kotlincForIdeWithStandardNaming("kotlinc.kotlinx-serialization-compiler-plugin", version),
        kotlincForIdeWithStandardNaming("kotlinc.lombok-compiler-plugin", version),
        kotlincForIdeWithStandardNaming("kotlinc.low-level-api-fir", version),
        kotlincForIdeWithStandardNaming("kotlinc.noarg-compiler-plugin", version),
        kotlincForIdeWithStandardNaming("kotlinc.parcelize-compiler-plugin", version),
        kotlincForIdeWithStandardNaming("kotlinc.sam-with-receiver-compiler-plugin", version),
        singleJarMvnLib("kotlinc.compiler-components-for-jps", "$ktGroup:compiler-components-for-jps:$version", transitive = false),
        singleJarMvnLib("kotlinc.kotlin-scripting-common", "$ktGroup:kotlin-scripting-common:$version", transitive = false),
        singleJarMvnLib("kotlinc.kotlin-scripting-compiler-impl", "$ktGroup:kotlin-scripting-compiler-impl:$version", transitive = false),
        singleJarMvnLib("kotlinc.kotlin-scripting-compiler", "$ktGroup:kotlin-scripting-compiler:$version", transitive = false),
        singleJarMvnLib("kotlinc.kotlin-scripting-jvm", "$ktGroup:kotlin-scripting-jvm:$version", transitive = false),
        singleJarMvnLib("kotlinc.kotlin-script-util", "$ktGroup:kotlin-script-util:$version", transitive = false),
        singleJarMvnLib("kotlinc.kotlin-reflect", "$ktGroup:kotlin-reflect:$version", excludes = listOf(MavenId(ktGroup, "kotlin-stdlib"))),
        singleJarMvnLib("kotlin-script-runtime", "$ktGroup:kotlin-script-runtime:$version"),
        run {
            val mavenIds = listOf(
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-jdk8:$version"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib:$version"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-common:$version"),
                MavenId.fromCoordinates("$ktGroup:kotlin-stdlib-jdk7:$version")
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
    require(name.startsWith("kotlinc."))
    return singleJarMvnLib(
        name,
        "$ktGroup:${name.removePrefix("kotlinc.")}-for-ide:$version",
        transitive = false,
        includeSources = includeSources
    )
}
