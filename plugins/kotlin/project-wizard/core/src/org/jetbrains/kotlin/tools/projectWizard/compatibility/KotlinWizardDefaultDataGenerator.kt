// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataParser
import org.jetbrains.plugins.gradle.jvmcompat.IdeVersionedDataState
import org.jetbrains.plugins.gradle.jvmcompat.readAppVersion
import java.io.StringWriter
import java.nio.charset.Charset
import java.nio.file.Paths
import java.time.LocalDate


class WizardDefaultDataGeneratorSettings<T : IdeVersionedDataState>(
    val jsonPath: String,
    val ktFileName: String,
    val parser: IdeVersionedDataParser<T>,
    val templatePath: String,
    val contextProvider: (T).(context: VelocityContext) -> Unit
) {
    companion object {
        fun getGenerators(): List<WizardDefaultDataGeneratorSettings<out IdeVersionedDataState>> = listOf(
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/dependencies.json",
                ktFileName = "DependencyDefaultData.kt",
                parser = DependencyVersionParser,
                templatePath = "compatibility/templates/KotlinDependencyDefaultData.kt.vm",
                contextProvider = DependencyVersionState::provideDefaultDataContext
            ),
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/kotlin_gradle_compatibility.json",
                ktFileName = "KotlinGradleCompatibilityDefaultData.kt",
                parser = KotlinGradleCompatibilityParser,
                templatePath = "compatibility/templates/KotlinGradleCompatibilityDefaultData.kt.vm",
                contextProvider = KotlinGradleCompatibilityState::provideDefaultDataContext
            ),
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/wizard_versions.json",
                ktFileName = "KotlinWizardVersionDefaultData.kt",
                parser = KotlinWizardVersionParser,
                templatePath = "compatibility/templates/KotlinWizardVersionDefaultData.kt.vm",
                contextProvider = KotlinWizardVersionState::provideDefaultDataContext
            ),
            WizardDefaultDataGeneratorSettings(
                jsonPath = "/compatibility/kotlin_libraries.json",
                ktFileName = "KotlinLibrariesDefaultData.kt",
                parser = KotlinLibrariesCompatibilityParser,
                templatePath = "compatibility/templates/KotlinLibrariesDefaultData.kt.vm",
                contextProvider = KotlinLibrariesCompatibilityState::provideDefaultDataContext
            ),
        )
    }

    fun generateDefaultData(applicationVersion: String): String {
        val jsonData = WizardDefaultDataGeneratorSettings::class.java.getResourceAsStream(jsonPath)!!
            .readAllBytes()
            .toString(Charset.forName("utf8"))
        val parsedData = parser.parseVersionedJson(jsonData, applicationVersion)!!

        val velocityEngine = VelocityEngine()
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath")
        velocityEngine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader::class.java.name)
        velocityEngine.init()

        val template = velocityEngine.getTemplate(templatePath)
        val context = VelocityContext()

        context.put("YEAR", LocalDate.now().year)
        contextProvider(parsedData, context)
        val writer = StringWriter()
        template.merge(context, writer)

        return writer.toString()
    }
}


internal fun main(args: Array<String>) {
    assert(args.size == 1) { "Should be 1 arg: Path to project" }
    val applicationInfo = Paths.get(args[0], "ultimate/ultimate-resources/resources/idea/ApplicationInfo.xml")
    val generatedFolder = Paths.get(args[0], "community/plugins/kotlin/project-wizard/core/generated")
    val applicationVersion = readAppVersion(applicationInfo)

    WizardDefaultDataGeneratorSettings.getGenerators().forEach { settings ->
        val generatedData = settings.generateDefaultData(applicationVersion)
        val outputFile = generatedFolder.resolve(settings.ktFileName).toFile()
        outputFile.parentFile.mkdirs()
        outputFile.writeText(generatedData, Charsets.UTF_8)
    }
}
