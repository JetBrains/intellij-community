/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.util.delegatorpatcher

import java.io.File
import java.util.*

enum class Mode { // See kotlinc-delegator/README
    MVN, // Compilation in "binary dependency on the compiler from maven artifacts" mode. Any IDEA release branch (master, 203, 202, 201, ...)
    SRC, // Compilation in "source dependency on the compiler" mode. Any `kt-` branch
}

object Config {
    private val properties = Properties()

    init {
        val resourceName = "model.properties"
        val modelResource = Config::class.java.getResource("/$resourceName") ?: error("Can't find resource '$resourceName'")
        modelResource.openStream().use { properties.load(it) }
    }

    private val mvnVersion = properties.getProperty("kotlinc.version")
    private const val BOOTSTRAP_KOTLIN_COMPILER_VERSION = "1.4.255-SNAPSHOT"
    fun getKotlincVersion(mode: Mode): String = when (mode) {
        Mode.MVN -> mvnVersion
        Mode.SRC -> BOOTSTRAP_KOTLIN_COMPILER_VERSION
    }
}

const val MODULE_DIR = "\$MODULE_DIR\$"
const val MAVEN_REPOSITORY = "\$MAVEN_REPOSITORY\$"
const val PROJECT_DIR = "\$PROJECT_DIR\$"
const val THIS_MODULE_NAME = "util.delegator-patcher"

enum class LibraryLevel {
    MODULE, PROJECT
}

class MavenArtifact(
    private val groupId: String,
    val artifactId: String,
    val fixatedVersion: String? = null,
    private val dependencies: List<MavenArtifact> = emptyList()
) {
    val flatMapDependencies: List<MavenArtifact> by lazy { listOf(this) + dependencies.flatMap { it.flatMapDependencies } }
    fun getBootstrapArtifactJar(mode: Mode, libraryLevel: LibraryLevel) = getBootstrapArtifactJarInternal(getVersion(mode), libraryLevel)
    fun getMavenArtifactJar(mode: Mode) = getMavenArtifactJarInternal(getVersion(mode))
    fun getMavenArtifactSources(mode: Mode) = "$MAVEN_REPOSITORY/${getArtifactPathRelativeToMavenRepo(getVersion(mode))}-sources.jar"
    fun coordinates(mode: Mode) = "$groupId:$artifactId:${getVersion(mode)}"
    fun patchProjectModelFile(xml: File, libraryLevel: LibraryLevel, mode: Mode) {
        val text = xml.readText()
        val replacement = Regex.escapeReplacement(
            when (mode) {
                Mode.MVN -> getMavenArtifactJarInternal(Config.getKotlincVersion(mode))
                Mode.SRC -> getBootstrapArtifactJarInternal(Config.getKotlincVersion(mode), libraryLevel)
            }
        )
        binModeRegex.replace(text, replacement)
        srcModeModuleLevelRegex.replace(text, replacement)
        srcModeProjectLevelRegex.replace(text, replacement)
    }

    private val versionRegex = """[\d\w\.\-]+?"""
    private val binModeRegex = Regex(getMavenArtifactJarInternal("VERSION").escape().replace("VERSION", versionRegex))
    private val srcModeProjectLevelRegex = Regex(getBootstrapArtifactJarInternal("VERSION", LibraryLevel.PROJECT).escape().replace("VERSION", versionRegex))
    private val srcModeModuleLevelRegex = Regex(getBootstrapArtifactJarInternal("VERSION", LibraryLevel.MODULE).escape().replace("VERSION", versionRegex))
    private fun getVersion(mode: Mode): String = fixatedVersion ?: Config.getKotlincVersion(mode)
    private fun getArtifactPathRelativeToMavenRepo(version: String) = listOf(
        groupId.replace(".", "/"),
        artifactId,
        version,
        "$artifactId-$version"
    ).joinToString("/")
    private fun getArtifactPrefixJarInternal(version: String) = "${getArtifactPathRelativeToMavenRepo(version)}.jar"
    private fun getMavenArtifactJarInternal(version: String) = "$MAVEN_REPOSITORY/${getArtifactPrefixJarInternal(version)}"
    private fun getBootstrapArtifactJarInternal(version: String, libraryLevel: LibraryLevel): String {
        if (groupId != "org.jetbrains.kotlin") {
            return getMavenArtifactJarInternal(version)
        }
        val relativeToKotlinIde = "kotlinc/prepare/ide-plugin-dependencies/$artifactId/build/libs/$artifactId-$version.jar"
        return when (libraryLevel) {
            LibraryLevel.PROJECT -> "$PROJECT_DIR/$relativeToKotlinIde"
            LibraryLevel.MODULE -> "$MODULE_DIR/../../../$relativeToKotlinIde"
        }
    }
    private fun String.escape() = replace(".", "\\.").replace("$", "\\$").replace("/", "\\/")
}

class Delegator(
    private val identifier: String,
    private val mavenArtifact: MavenArtifact,
    private val libraryLevel: LibraryLevel,
    private val kotlincTestModules: List<String> = emptyList(),
    private val forceMvnModeOnly: Boolean = false
) {
    constructor(
        identifier: String,
        groupId: String = "org.jetbrains.kotlin",
        libraryLevel: LibraryLevel = LibraryLevel.MODULE,
        kotlincTestModules: List<String> = emptyList()
    ) : this(identifier, MavenArtifact(groupId, "$identifier-for-ide"), libraryLevel, kotlincTestModules)

    private fun generateModuleImlContent(projectRoot: File, mode: Mode) = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<module type="JAVA_MODULE" version="4">
        |  <component name="NewModuleRootManager" inherit-compiler-output="true">
        |    <exclude-output />
        |    <content url="file://$MODULE_DIR" />
        |    <orderEntry type="inheritedJdk" />
        |    <orderEntry type="sourceFolder" forTests="false" />
        |    ${getModuleLibrary(projectRoot, mode)}
        |    ${getKotlincModules(projectRoot, mode)}
        |  </component>
        |</module>
    """.trimMarginWithInterpolations().lineSequence().filter { it.isNotBlank() }.joinToString("\n")

    private fun generateProjectLibraryImlContent(mode: Mode) = """
        |<?xml version="1.0" encoding="UTF-8"?>
        |<component name="libraryTable">
        |  ${getLibraryWithActualJars(mode)}
        |</component>
    """.trimMarginWithInterpolations().lineSequence().filter { it.isNotBlank() }.joinToString("\n")

    private fun getModuleLibrary(projectRoot: File, mode: Mode): String {
        val scope = if (getKotlincModules(projectRoot, mode).isNotBlank()) " scope=\"RUNTIME\"" else ""
        return when (libraryLevel) {
            LibraryLevel.MODULE -> """
                |<orderEntry type="module-library" exported=""$scope>
                |  ${getLibraryWithActualJars(mode)}
                |</orderEntry>
            """.trimMarginWithInterpolations()
            LibraryLevel.PROJECT -> """<orderEntry type="library" exported=""$scope name="kotlinc.$identifier" level="project" />"""
        }
    }

    private fun getKotlincModules(projectRoot: File, mode: Mode) = when (mode) {
        Mode.MVN -> ""
        Mode.SRC -> {
            val kotlinMainModules = mavenArtifact.flatMapDependencies
                .map {
                    projectRoot.resolve("kotlinc/build/artifacts-for-ide-to-modules-mapping/${it.artifactId}.txt")
                }
                .filter { it.exists() }
                .flatMap { it.readLines() }
                .map { "kotlin${it.replace(":", ".")}.main" }
            (kotlinMainModules + kotlincTestModules).joinToString("\n") {
                """<orderEntry type="module" module-name="$it" exported="" scope="PROVIDED" />"""
            }
        }
    }

    private fun getLibraryWithActualJars(mode: Mode) = when (mode) {
        Mode.MVN -> """
            |<library name="kotlinc.$identifier" type="repository">
            |  <properties${if (mavenArtifact.flatMapDependencies.size > 1) "" else "include-transitive-deps=\"false\" "} maven-id="${mavenArtifact.coordinates(mode)}" />
            |  <CLASSES>
            |    ${getClasses(mode)}
            |  </CLASSES>
            |  <JAVADOC />
            |  <SOURCES>
            |    ${getSourcesForMvnMode()}
            |  </SOURCES>
            |</library>
        """.trimMarginWithInterpolations()
        Mode.SRC -> """
            |<library name="kotlinc.$identifier">
            |  <CLASSES>
            |    ${getClasses(mode)}
            |  </CLASSES>
            |  <JAVADOC />
            |  <SOURCES />
            |</library>
        """.trimMarginWithInterpolations()
    }

    private fun getSourcesForMvnMode() = mavenArtifact.flatMapDependencies.joinToString("\n") {
        """<root url="jar://${it.getMavenArtifactSources(Mode.MVN)}!/" />"""
    }

    private fun getClasses(mode: Mode) = mavenArtifact.flatMapDependencies.joinToString("\n") {
        when (mode) {
            Mode.SRC -> """<root url="jar://${it.getBootstrapArtifactJar(mode, libraryLevel)}!/" />"""
            Mode.MVN -> """<root url="jar://${it.getMavenArtifactJar(mode)}!/" />"""
        }
    }

    fun patchProjectModelFiles(projectRoot: File, kotlinRoot: File, _mode: Mode) {
        val mode = if (forceMvnModeOnly) Mode.MVN else _mode
        val dotIdea = projectRoot.resolve(".idea")
        if (libraryLevel == LibraryLevel.PROJECT) {
            val libraryXml = dotIdea.resolve("libraries/kotlinc_${identifier.replace("-", "_")}.xml")
            check(libraryXml.exists())
            libraryXml.writeText(generateProjectLibraryImlContent(mode))
        }
        val moduleIml = kotlinRoot.resolve("kotlinc-delegator/$identifier/kotlinc-delegator.$identifier.iml")
        check(moduleIml.exists())
        moduleIml.writeText(generateModuleImlContent(projectRoot, mode))
        dotIdea.walkTopDown().filter { it.extension == "xml" }.forEach { xml ->
            mavenArtifact.patchProjectModelFile(xml, libraryLevel, mode)
        }
    }
}

fun main(args: Array<String>) {
    val mode = Mode.valueOf(args.single().toUpperCase())
    val projectRootPublic = generateSequence(File(".").canonicalFile) { it.parentFile }.first { it.resolve("kotlin.intellij-kotlin.iml").exists() }
    val projectRootPrivate = projectRootPublic.resolve("..").takeIf { it.resolve("kotlin.kotlin-ide.iml").exists() }
    for ((projectRoot, kotlinRoot) in listOfNotNull(projectRootPublic to projectRootPublic, projectRootPrivate?.let { it to it.resolve("kotlin") })) {
        val delegatorsDir = kotlinRoot.resolve("kotlinc-delegator")
        val realModules = delegatorsDir.listFiles()!!.filter { it.isDirectory && it.name != THIS_MODULE_NAME }
        val delegators = getDelegators(projectRoot, mode)
        check(delegators.size == realModules.size) {
            "delegators size (${delegators.size}) and modules number (${realModules.size}) are expected to be equal"
        }
        if (mode == Mode.SRC) {
            println("--- Running kotlinc gradle! ---")
            // jarsForIde task will produce artifacts-for-ide-to-modules-mapping
            val exitCode = ProcessBuilder("./gradlew", "jarsForIde")
                .directory(projectRoot.resolve("kotlinc"))
                .inheritIO()
                .start()
                .waitFor()
            check(exitCode == 0) { "kotlinc gradle failed with exitCode $exitCode" }
            println("--- Ended running kotlinc gradle ---")
        }
        delegators.forEach { it.patchProjectModelFiles(projectRoot, kotlinRoot, mode) }
    }
}

fun String.trimMarginWithInterpolations(): String {
    val regex = Regex("""^(\s*\|)(\s*).*$""")
    val out = mutableListOf<String>()
    var prevIndent = ""
    for (line in lines()) {
        val matchResult = regex.matchEntire(line)
        if (matchResult != null) {
            out.add(line.removePrefix(matchResult.groupValues[1]))
            prevIndent = matchResult.groupValues[2]
        } else {
            out.add(prevIndent + line)
        }
    }
    return out.joinToString("\n")
}
