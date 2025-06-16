#!/usr/bin/env kotlin

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.19.0")
@file:DependsOn("com.fasterxml.jackson.core:jackson-core:2.19.0")

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.system.exitProcess

val argument = Configuration.NEW_VERSION
    ?: args.firstOrNull()
    ?: errorMessageAndExit(
        """
        Usage: ./advance-analyzer-version.main.kts <version>
           or: ./advance-analyzer-version.main.kts --bootstrap
           
           version – A version to advance to.
        """.trimIndent()
    )

object Configuration {
    /** Can be used locally instead of passing a version as a script parameter */
    val NEW_VERSION: String? = null

    const val TEAMCITY_URL = "https://buildserver.labs.intellij.net"
    const val CONFIG_ID = "Kotlin_KotlinDev_BuildNumber"
    const val MODEL_PROPERTIES_FILE_NAME = "model.properties"
    const val MODEL_PROPERTIES_FILE_PATH = "./resources/$MODEL_PROPERTIES_FILE_NAME"
    const val KOTLINC_VERSION_KEY = "kotlincVersion"
    const val KOTLINC_ARTIFACTS_MODE_KEY = "kotlincArtifactsMode"

    /**
     * Describes [pathspec](https://git-scm.com/docs/gitglossary#Documentation/gitglossary.txt-aiddefpathspecapathspec)
     * for files which are affected by the compiler version advancement.
     */
    val RELATED_FILES_PATH_SPECS = arrayOf(
      ":/*/libraries/kotlinc_*.xml",
      ":/*/plugins/kotlin/util/project-model-updater/resources/model.properties",
      ":/*/runConfigurations/Kotlin_Coop__Publish_compiler_for_ide_JARs.xml",
    )
}

fun errorMessageAndExit(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

val jacksonObjectMapper = jacksonObjectMapper()

fun findVcsRevision(buildNumber: String): String {
    val url = URL(
        "${Configuration.TEAMCITY_URL}/app/rest/builds?locator=buildType:${Configuration.CONFIG_ID},number:$buildNumber&fields=build(id,number,revisions(revision(version)))&guest=1"
    )

    val connection = url.openConnection() as HttpURLConnection
    connection.setRequestProperty("Accept", "application/json")

    if (connection.responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
        errorMessageAndExit("Unable to get access to TeamCity. Make sure you are inside the corporate network")
    }

    connection.getInputStream().use { input ->
        val root = jacksonObjectMapper.readTree(input)

        /**
         * Example output:
         * {
         *   "build" : [ {
         *     "id" : 5169957,
         *     "number" : "2.2.20-dev-3391",
         *     "revisions" : {
         *       "revision" : [ {
         *         "version" : "dd5e441f485163215aa2f63dcc46e3d1851453ed"
         *       } ]
         *     }
         *   } ]
         * }
         */

        val builds = root["build"] ?: errorMessageAndExit("No 'build' element in the response. Response: ${root.toPrettyString()}")
        val build = builds[0] ?: errorMessageAndExit("No build found for '$buildNumber'")
        val revisions = build["revisions"]
            ?: errorMessageAndExit("No 'revisions' field found for '$buildNumber'. Build output: ${build.toPrettyString()}")

        val revision = revisions["revision"]
            ?: errorMessageAndExit("No revision found for build '$buildNumber'. Build output: ${revisions.toPrettyString()}")

        val versionNode = revision[0]
            ?: errorMessageAndExit("No version found for build '$buildNumber'. Build output: ${revision.toPrettyString()}")

        val version = versionNode["version"]?.textValue()
            ?: errorMessageAndExit("No version found for build '$buildNumber'. Version node: ${versionNode.toPrettyString()}")

        return version
    }
}

fun modelPropertiesPath(): Path {
    val propertiesFilePath = Configuration.MODEL_PROPERTIES_FILE_PATH
    val path = Path(propertiesFilePath)
    if (path.notExists()) {
        errorMessageAndExit("File '$propertiesFilePath' doesn't exist")
    }

    return path
}

fun readBaseCurrentVersion(): String {
    val path = modelPropertiesPath()

    val properties = Properties().apply {
        path.inputStream().use(this::load)
    }

    return properties.getProperty(Configuration.KOTLINC_VERSION_KEY) ?: errorMessageAndExit("No '${Configuration.KOTLINC_VERSION_KEY}' found in '${path.name}'")
}

fun githubCompareLink(baseRevision: String, newRevision: String): String {
    val shortText = "${baseRevision.subSequence(0, 8)}...${newRevision.subSequence(0, 8)}"
    return "[$shortText](https://github.com/JetBrains/kotlin/compare/$baseRevision...$newRevision)"
}

fun findGitRoot(): Path = generateSequence(Path(".").toAbsolutePath()) { it.parent }
    .find { it.resolve(".git").exists() }
    ?: errorMessageAndExit("Git repository root not found")

/** Git root is required for proper work of some commands */
val gitRootPath = findGitRoot()

fun modifiedFiles(): List<String> = ProcessBuilder("git", "status", "--porcelain", "--", *Configuration.RELATED_FILES_PATH_SPECS)
    .directory(gitRootPath.toFile())
    .start()
    .inputStream
    .bufferedReader()
    .readLines()
    .map {
        it.substringAfterLast(" ").trim()
    }

fun checkUncommittedChanges() {
    println("Checking for uncommitted changes...")

    val modifiedFiles = modifiedFiles()
    if (modifiedFiles.isNotEmpty()) {
        errorMessageAndExit("Found uncommitted changes in related files:\n${modifiedFiles.joinToString("\n")}")
    }

    println("No uncommitted related files found")
}

fun updateModelProperties(contentUpdate: (String) -> String) {
    println("Updating ${Configuration.MODEL_PROPERTIES_FILE_NAME}...")
    val path = modelPropertiesPath()
    val oldContent = path.readText()
    val newContent = contentUpdate(oldContent)
    path.writeText(newContent)

    println("Updated ${Configuration.MODEL_PROPERTIES_FILE_NAME}")
}

fun errorMessageAndExitWithRollback(message: String): Nothing {
    System.err.println(message)
    println("Rolling back changes...")

    val modifiedFiles = modifiedFiles()
    if (modifiedFiles.isNotEmpty()) {
        val exitCode = ProcessBuilder(listOf("git", "restore", "--staged", "--worktree", "--") + modifiedFiles)
            .directory(gitRootPath.toFile())
            .inheritIO()
            .start()
            .waitFor()

        if (exitCode != 0) {
            System.err.println("Failed to rollback changes")
        } else {
            println("Successfully rolled back changes for:\n${modifiedFiles.joinToString("\n")}")
        }
    } else {
        println("No files to rollback")
    }

    exitProcess(1)
}

fun updateLibraries() {
    println("Updating libraries...")
    val exitCode = ProcessBuilder("gradle", "run")
        .directory(File("."))
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        errorMessageAndExitWithRollback("Libraries update failed")
    }

    println("Updated libraries")
}

fun commitChanges(title: String, description: String? = null) {
    println("Committing changes...")
    val modifiedFiles = modifiedFiles()
    if (modifiedFiles.isEmpty()) {
        errorMessageAndExit("No changes found")
    }

    val exitCode = ProcessBuilder(
        buildList {
            add("git"); add("commit")
            add("-m"); add(title)
            if (description != null) {
                add("-m"); add(description)
            }

            add("--")
            addAll(modifiedFiles)
        }
    )
        .directory(gitRootPath.toFile())
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        errorMessageAndExitWithRollback("Commit failed")
    }

    println("Committed")
}

checkUncommittedChanges()

val baseVersion = readBaseCurrentVersion()
println("Base version: $baseVersion")

if (argument == "--bootstrap") {
    println("Bootstrapping...")
    updateModelProperties { content ->
        content.replace("${Configuration.KOTLINC_ARTIFACTS_MODE_KEY}=MAVEN", "${Configuration.KOTLINC_ARTIFACTS_MODE_KEY}=BOOTSTRAP")
    }

    updateLibraries()
    commitChanges(title = "[kotlin] restore cooperative development")
} else {
    println("Advancing...")

    val newVersion = argument
    println("New version: $newVersion")

    val baseRevision = findVcsRevision(baseVersion)
    println("Base Kotlin Git revision: $baseRevision")

    val newRevision = findVcsRevision(newVersion)
    println("New Kotlin Git revision: $newRevision")

    val changesDescription = "Includes: " + if (baseRevision != newRevision)
        githubCompareLink(baseRevision, newRevision)
    else
        "no new changes"

    println(changesDescription)
    updateModelProperties { content ->
        content.replace("${Configuration.KOTLINC_VERSION_KEY}=$baseVersion", "${Configuration.KOTLINC_VERSION_KEY}=$newVersion")
            .replace("${Configuration.KOTLINC_ARTIFACTS_MODE_KEY}=BOOTSTRAP", "${Configuration.KOTLINC_ARTIFACTS_MODE_KEY}=MAVEN")
    }

    updateLibraries()
    commitChanges(
        title = "[kotlin] advance kotlinc version for analyzer to $newVersion",
        description = changesDescription,
    )
}