// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataState
import org.jetbrains.plugins.gradle.jvmcompat.readAppVersion
import java.nio.charset.Charset
import java.nio.file.Paths

class WizardDefaultDataGeneratorSettings<T : IdeVersionedDataState>(
    val jsonPath: String,
    val ktFileName: String,
    val parser: IdeVersionedDataParser<T>,
    val generator: (T).() -> String
) {
    companion object {
        fun getGenerators() = listOf(
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/dependencies.json",
                ktFileName = "DependencyDefaultData.kt",
                parser = DependencyVersionParser,
                generator = DependencyVersionState::generateDefaultData
            ),
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/kotlin_gradle_compatibility.json",
                ktFileName = "KotlinGradleCompatibilityDefaultData.kt",
                parser = KotlinGradleCompatibilityParser,
                generator = KotlinGradleCompatibilityState::generateDefaultData
            ),
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/wizard_versions.json",
                ktFileName = "KotlinWizardVersionDefaultData.kt",
                parser = KotlinWizardVersionParser,
                generator = KotlinWizardVersionState::generateDefaultData
            )
        )
    }

    fun generateDefaultData(applicationVersion: String): String {
        val jsonData = WizardDefaultDataGeneratorSettings::class.java.getResourceAsStream(jsonPath)!!
            .readAllBytes()
            .toString(Charset.forName("utf8"))
        val parsedData = parser.parseVersionedJson(jsonData, applicationVersion)!!

        return generator(parsedData)
    }
}


internal fun main(args: Array<String>) {
    assert(args.size == 1) { "Should be 1 arg: Path to project" }
    val applicationInfo = Paths.get(args[0], "ultimate/ultimate-resources/resources/idea/ApplicationInfo.xml")
    val generatedFolder = Paths.get(args[0], "community/plugins/kotlin/project-wizard/core/generated")
    val applicationVersion = readAppVersion(applicationInfo)

    WizardDefaultDataGeneratorSettings.getGenerators().forEach { settings ->
        val generatedData = settings.generateDefaultData(applicationVersion)
        val outputFile = generatedFolder.resolve(settings.ktFileName)
        outputFile.toFile().writeText(generatedData, Charsets.UTF_8)
    }
}
