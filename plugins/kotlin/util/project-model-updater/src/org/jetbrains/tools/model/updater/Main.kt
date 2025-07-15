// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jdom.Document
import org.jetbrains.tools.model.updater.impl.*
import java.io.File
import java.util.*

class GeneratorPreferences(properties: Properties) : Preferences(properties) {
    val jpsPluginVersion: String by Preference()
    val jpsPluginArtifactsMode: ArtifactMode by Preference(ArtifactMode::valueOf)

    val kotlincVersion: String by Preference()
    val kotlinGradlePluginVersion: String by Preference()
    val kotlincArtifactsMode: ArtifactMode by Preference(ArtifactMode::valueOf)

    enum class ArtifactMode {
        MAVEN, BOOTSTRAP
    }

    companion object {
        fun parse(args: Array<String>): GeneratorPreferences {
            val properties = Properties()

            val configurationFile = object {}::class.java.getResource("/model.properties")
            configurationFile?.openStream()?.use { stream -> properties.load(stream) }

            // Preferences passed as command line arguments override those from a configuration file
            for (arg in args.flatMap { it.split(" ") }) {
                val parts = arg.split('=')
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid argument: $arg")
                }
                properties[parts[0]] = parts[1]
            }

            return GeneratorPreferences(properties)
        }
    }
}

fun main(args: Array<String>) {
    val preferences = GeneratorPreferences.parse(args)

    val communityRoot = generateSequence(File(".").canonicalFile) { it.parentFile }.first {
        it.resolve(".idea").isDirectory && !it.resolve("community").isDirectory && it.name != "project-model-updater"
    }.normalize()

    val monorepoRoot = communityRoot.resolve("..").takeIf { it.resolve(".idea").isDirectory }?.normalize()

    val resolverSettings = readJpsResolverSettings(communityRoot, monorepoRoot)

    communityRoot.resolve("plugins/kotlin/base/plugin/testResources/kotlincKotlinCompilerCliVersion.txt")
        .writeText(preferences.kotlincArtifactVersion)

    fun processRoot(root: File, isCommunity: Boolean) {
        println("Processing kotlinc libraries in root: $root")
        val libraries = generateKotlincLibraries(preferences, isCommunity)
        regenerateProjectLibraries(root.resolve(".idea"), libraries, resolverSettings)
    }

    if (monorepoRoot != null) {
        processRoot(monorepoRoot, isCommunity = false)
        cloneModuleStructure(monorepoRoot, communityRoot)
    }

    processRoot(communityRoot, isCommunity = true)
    updateLatestGradlePluginVersion(communityRoot, preferences.kotlinGradlePluginVersion)
    updateKGPVersionForKotlinNativeTests(communityRoot, preferences.kotlinGradlePluginVersion)
    updateCoopRunConfiguration(monorepoRoot, communityRoot)
}

private fun regenerateProjectLibraries(dotIdea: File, libraries: List<JpsLibrary>, resolverSettings: JpsResolverSettings) {
    val librariesDir = dotIdea.resolve("libraries")
    librariesDir.listFiles { file -> file.name.startsWith("kotlinc_") }!!.forEach {
        println("Removing $it")
        it.delete()
    }

    for (library in libraries) {
        val libraryFileName = library.name.replace("\\W".toRegex(), "_") + ".xml"
        val xmlFile = librariesDir.resolve(libraryFileName)
        println("Writing $xmlFile")
        xmlFile.writeText(library.render(resolverSettings))
    }
}

private fun cloneModuleStructure(monorepoRoot: File, communityRoot: File) {
    val monorepoModulesFile = monorepoRoot.resolve(".idea/modules.xml")
    val communityModulesFile = communityRoot.resolve(".idea/modules.xml")

    val monorepoModulesXml = monorepoModulesFile.readXml()
    val communityModulesXml = communityModulesFile.readXml()

    val monorepoModules = readModules(monorepoRoot, monorepoModulesXml)

    val communityModules = monorepoModules
        .filterValues { module -> module.isCommunity && module.dependencies.all { dep -> monorepoModules[dep]?.isCommunity ?: false } }
        .mapValues { (_, module) -> module.copy(path = module.path.removePrefix("community/")) }

    // Leave community renames as is. They're rarely changed, and it seems there are a number of old ones
    val communityModuleRenames = readModuleRenames(communityModulesXml)

    val newCommunityModulesXmlContent = xml("project", "version" to "4") {
        xml("component", "name" to "ProjectModuleManager") {
            xml("modules") {
                for (module in communityModules.values) {
                    val modulePath = "\$PROJECT_DIR$/${module.path}"
                    xml("module", "fileurl" to "file://$modulePath", "filepath" to modulePath)
                }
            }
        }
    }

    communityModulesFile.writeText(newCommunityModulesXmlContent.render(addXmlDeclaration = true))
}

private fun updateCoopRunConfiguration(monorepoRoot: File?, communityRoot: File) {
    val runConfigurationFilePath = ".idea/runConfigurations/Kotlin_Coop__Publish_compiler_for_ide_JARs.xml"
    val runConfiguration = communityRoot.resolve(runConfigurationFilePath)
    val originalText = runConfiguration.readText()
    val resultText = originalText.replace(bootstrapVersionRegex) { matchResult ->
        "${matchResult.groupValues[1]}$BOOTSTRAP_VERSION${matchResult.groupValues[4]}"
    }

    runConfiguration.writeText(resultText)
    monorepoRoot?.resolve(runConfigurationFilePath)?.writeText(resultText)
}

private val bootstrapVersionRegex = Regex("(-P(deployVersion|build\\.number)=)(.+?)([ \"])")

/**
 * Updates the `KotlinGradlePluginVersions.kt` source file to contain the latest [kotlinGradlePluginVersion]
 * in the source code. The `KotlinGradlePluginVersions` source file can't directly read the `model.properties` file directly, since
 * the project model can be overwritten by the [main] args (see also [GeneratorPreferences.parse])
 */
private fun updateLatestGradlePluginVersion(communityRoot: File, kotlinGradlePluginVersion: String) {
    val kotlinGradlePluginVersionsKt = communityRoot.resolve(
        "plugins/kotlin/gradle/gradle-java/tests.shared/test/org/jetbrains/kotlin/idea/codeInsight/gradle/KotlinGradlePluginVersions.kt"
    )
    updateFile(
        kotlinGradlePluginVersionsKt,
        """val latest = .*""",
        "val latest = KotlinToolingVersion(\"$kotlinGradlePluginVersion\")"
    )
}

private fun updateKGPVersionForKotlinNativeTests(communityRoot: File, kotlinGradlePluginVersion: String) {
    val kotlinNativeVersionsKt = communityRoot.resolve(
        "plugins/kotlin/base/plugin/test/org/jetbrains/kotlin/idea/artifacts/KotlinNativeVersion.kt"
    )
    updateFile(
        kotlinNativeVersionsKt,
        """private const val kotlinGradlePluginVersion: String =.*""",
        "private const val kotlinGradlePluginVersion: String = \"$kotlinGradlePluginVersion\""
    )
}

private fun updateFile(sourceFile: File, regexp: String, replacement: String) {
    val updatedFileContent = sourceFile.readText().replace(
        Regex(regexp), replacement
    )

    sourceFile.writeText(updatedFileContent)
}

private data class JpsModule(val name: String, val path: String, val dependencies: List<String>) {
    val isCommunity: Boolean
        get() = path.startsWith("community/")
}

private fun readModules(root: File, document: Document): Map<String, JpsModule> {
    val projectModuleManagerComponent = document.rootElement.getChildren("component")
        .first { it.getAttributeValue("name") == "ProjectModuleManager" }

    val result = LinkedHashMap<String, JpsModule>()

    for (moduleEntry in projectModuleManagerComponent.getChild("modules").getChildren("module")) {
        val modulePath = moduleEntry.getAttributeValue("filepath").removePrefix("\$PROJECT_DIR$/")
        val moduleName = modulePath.substringAfterLast("/").removeSuffix(".iml")

        val moduleXml = root.resolve(modulePath).readXml()
        val moduleRootManagerComponent = moduleXml.rootElement.getChildren("component")
            .first { it.getAttributeValue("name") == "NewModuleRootManager" }

        val dependencies = moduleRootManagerComponent.getChildren("orderEntry")
            .filter { it.getAttributeValue("type") == "module" }
            .mapNotNull { it.getAttributeValue("module-name") }

        result[moduleName] = JpsModule(moduleName, modulePath, dependencies)
    }

    return result
}

private fun readModuleRenames(document: Document): Map<String, String> {
    val moduleRenamingHistoryComponent = document.rootElement.getChildren("component")
        .firstOrNull { it.getAttributeValue("name") == "ModuleRenamingHistory" }
        ?: return emptyMap()

    val result = mutableMapOf<String, String>()
    for (moduleEntry in moduleRenamingHistoryComponent.getChildren("module")) {
        val oldName = moduleEntry.getAttributeValue("old-name") ?: continue
        val newName = moduleEntry.getAttributeValue("new-name") ?: continue
        result[oldName] = newName
    }
    return result
}
