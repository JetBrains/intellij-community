// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.idea.maven.importing.MavenApplicableConfigurator
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.createArguments
import org.jetbrains.kotlin.config.splitArgumentString
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator
import org.jetbrains.kotlin.platform.IdePlatformKind
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.CommonIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.platform.impl.isCommon
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File
import java.lang.ref.WeakReference

interface MavenProjectImportHandler {
    companion object : ProjectExtensionDescriptor<MavenProjectImportHandler>(
        "org.jetbrains.kotlin.mavenProjectImportHandler",
        MavenProjectImportHandler::class.java
    )

    operator fun invoke(facetSettings: IKotlinFacetSettings, mavenProject: MavenProject)
}

open class KotlinMavenImporter : MavenApplicableConfigurator(KOTLIN_PLUGIN_GROUP_ID, KOTLIN_PLUGIN_ARTIFACT_ID) {
    companion object {
        const val KOTLIN_PLUGIN_GROUP_ID: String = "org.jetbrains.kotlin"
        const val KOTLIN_PLUGIN_ARTIFACT_ID: String = "kotlin-maven-plugin"

        const val KOTLIN_PLUGIN_SOURCE_DIRS_CONFIG: String = "sourceDirs"

        private val LOG = logger<KotlinMavenImporter>()

        internal val KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED = Key<Boolean>("KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED")
        val KOTLIN_JPS_VERSION_ACCUMULATOR: Key<IdeKotlinVersion> = Key<IdeKotlinVersion>("KOTLIN_JPS_VERSION_ACCUMULATOR")
    }

    protected data class ImportedArguments(val args: List<String>, val jvmTarget6IsUsed: Boolean)

    protected fun getCompilerArgumentsByConfigurationElement(
        mavenProject: MavenProject,
        configuration: Element?,
        platform: TargetPlatform,
        project: Project
    ): ImportedArguments {
        val arguments = platform.createArguments()
        var jvmTarget6IsUsed = false

        arguments.apiVersion =
            configuration?.getChild("apiVersion")?.text ?: mavenProject.properties["kotlin.compiler.apiVersion"]?.toString()
        arguments.languageVersion =
            configuration?.getChild("languageVersion")?.text ?: mavenProject.properties["kotlin.compiler.languageVersion"]?.toString()
        arguments.multiPlatform = configuration?.getChild("multiPlatform")?.text?.trim()?.toBoolean() ?: false
        arguments.suppressWarnings = configuration?.getChild("nowarn")?.text?.trim()?.toBoolean() ?: false

        when (arguments) {
            is K2JVMCompilerArguments -> {
                arguments.classpath = configuration?.getChild("classpath")?.text
                arguments.jdkHome = configuration?.getChild("jdkHome")?.text
                arguments.javaParameters = configuration?.getChild("javaParameters")?.text?.toBoolean() ?: false

                val jvmTarget = configuration?.getChild("jvmTarget")?.text ?: mavenProject.properties["kotlin.compiler.jvmTarget"]?.toString()
                val jpsVersion = KotlinJpsPluginSettings.getInstance(project).settings.version
                LOG.debug("Found JPS version ", jpsVersion)

                if (jvmTarget == JvmTarget.JVM_1_6.description && jpsVersion.isBlank()) {
                    // Load JVM target 1.6 in Maven projects as 1.8, for IDEA platforms <= 222.
                    // The reason is that JVM target 1.6 is no longer supported by the latest Kotlin compiler, yet we'd like JPS projects imported from
                    // Maven to be compilable by IDEA, to avoid breaking local development.
                    // (Since IDEA 222, JPS plugin is unbundled from the Kotlin IDEA plugin, so this change is unnecessary there in case
                    // when an explicit version is specified in kotlinc.xml)
                    arguments.jvmTarget = JvmTarget.JVM_1_8.description
                    jvmTarget6IsUsed = true
                    LOG.info("Using JVM target 1.8 rather than 1.6")
                } else {
                    arguments.jvmTarget = jvmTarget
                }
                LOG.debug("Using JVM target ", jvmTarget)
            }

            is K2JSCompilerArguments -> {
                arguments.sourceMap = configuration?.getChild("sourceMap")?.text?.trim()?.toBoolean() ?: false
                arguments.sourceMapPrefix = configuration?.getChild("sourceMapPrefix")?.text?.trim() ?: ""
                arguments.sourceMapEmbedSources = configuration?.getChild("sourceMapEmbedSources")?.text?.trim() ?: "inlining"
                arguments.outputDir = configuration?.getChild("outputFile")?.text?.let { File(it).parent }
                arguments.moduleName = configuration?.getChild("outputFile")?.text?.let { File(it).nameWithoutExtension }
                arguments.moduleKind = configuration?.getChild("moduleKind")?.text
                arguments.main = configuration?.getChild("main")?.text
            }
        }

        val additionalArgs = SmartList<String>().apply {
            val argsElement = configuration?.getChild("args")

            argsElement?.content?.forEach { argElement ->
                when (argElement) {
                    is Text -> {
                        argElement.text?.let { addAll(splitArgumentString(it)) }
                    }

                    is Element -> {
                        if (argElement.name == "arg") {
                            addIfNotNull(argElement.text)
                        }
                    }
                }
            }
        }
        parseCommandLineArguments(additionalArgs, arguments)

        return ImportedArguments(ArgumentUtils.convertArgumentsToStringList(arguments), jvmTarget6IsUsed)
    }

    protected fun displayJvmTarget6UsageNotification(project: Project) {
        NotificationGroupManager.getInstance()
          .getNotificationGroup("Kotlin Maven project import")
          .createNotification(
            KotlinMavenBundle.message("configuration.maven.jvm.target.1.6.title"),
            KotlinMavenBundle.message("configuration.maven.jvm.target.1.6.content"),
            NotificationType.WARNING,
          )
          .setImportant(true)
          .notify(project)
    }

    protected val compilationGoals: List<String> = listOf(
        PomFile.KotlinGoals.Compile,
        PomFile.KotlinGoals.TestCompile,
        PomFile.KotlinGoals.Js,
        PomFile.KotlinGoals.TestJs,
        PomFile.KotlinGoals.MetaData
    )

    protected val MavenPlugin.compilerVersion: IdeKotlinVersion
        get() = version?.let(IdeKotlinVersion::opt) ?: KotlinPluginLayout.standaloneCompilerVersion

    protected fun MavenProject.findKotlinMavenPlugin(): MavenPlugin? = findPlugin(
        KotlinMavenConfigurator.GROUP_ID,
        KotlinMavenConfigurator.MAVEN_PLUGIN_ID,
    )

    private var kotlinJsCompilerWarning = WeakReference<Notification>(null)

    protected fun deprecatedKotlinJsCompiler(
        project: Project,
        kotlinVersion: KotlinVersion,
    ) {

        if (!kotlinVersion.isAtLeast(1, 7)) return

        if (kotlinJsCompilerWarning.get() != null) return

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Kotlin/JS compiler Maven")
            .createNotification(
                KotlinMavenBundle.message("notification.text.kotlin.js.compiler.title"),
                KotlinMavenBundle.message("notification.text.kotlin.js.compiler.body"),
                NotificationType.WARNING
            )
            .addAction(
                BrowseNotificationAction(
                    KotlinMavenBundle.message("notification.text.kotlin.js.compiler.learn.more"),
                    KotlinMavenBundle.message("notification.text.kotlin.js.compiler.link"),
                )
            )
            .also {
                kotlinJsCompilerWarning = WeakReference(it)
            }
            .notify(project)
    }

    protected fun detectPlatform(mavenProject: MavenProject): IdePlatformKind? =
        detectPlatformByExecutions(mavenProject) ?: detectPlatformByLibraries(mavenProject)

    private fun detectPlatformByExecutions(mavenProject: MavenProject): IdePlatformKind? {
        return mavenProject.findPlugin(KOTLIN_PLUGIN_GROUP_ID, KOTLIN_PLUGIN_ARTIFACT_ID)?.executions?.flatMap { it.goals }
            ?.mapNotNull { goal ->
                when (goal) {
                    PomFile.KotlinGoals.Compile, PomFile.KotlinGoals.TestCompile -> JvmIdePlatformKind
                    PomFile.KotlinGoals.Js, PomFile.KotlinGoals.TestJs -> JsIdePlatformKind
                    PomFile.KotlinGoals.MetaData -> CommonIdePlatformKind
                    else -> null
                }
            }?.distinct()?.singleOrNull()
    }

    private fun detectPlatformByLibraries(mavenProject: MavenProject): IdePlatformKind? {
        for (kind in IdePlatformKind.ALL_KINDS) {
            val mavenLibraryIds = kind.tooling.mavenLibraryIds
            if (mavenLibraryIds.any { mavenProject.findDependencies(KOTLIN_PLUGIN_GROUP_ID, it).isNotEmpty() }) {
                // TODO we could return here the correct version
                return kind
            }
        }

        return null
    }

    fun collectSourceDirectories(mavenProject: MavenProject): List<Pair<SourceType, String>> =
        mavenProject.plugins.filter { it.isKotlinPlugin() }.flatMap { plugin ->
            plugin.configurationElement.sourceDirectories().map { SourceType.PROD to it } +
                    plugin.executions.flatMap { execution ->
                        execution.configurationElement.sourceDirectories().map { execution.sourceType() to it }
                    }
        }.distinct()

    fun setImplementedModuleName(facetSettings: IKotlinFacetSettings, mavenProject: MavenProject, project: Project) {
        if (facetSettings.targetPlatform.isCommon()) {
            facetSettings.implementedModuleNames = emptyList()
        } else {
            val manager = MavenProjectsManager.getInstance(project)
            val mavenDependencies = mavenProject.dependencies.mapNotNull { manager?.findProject(it) }
            val implemented = mavenDependencies.filter { detectPlatformByExecutions(it).isCommon }

            facetSettings.implementedModuleNames = implemented.map { manager.findModule(it)?.name ?: it.displayName }
        }
    }
}

fun MavenPlugin.isKotlinPlugin(): Boolean =
    groupId == KotlinMavenImporter.KOTLIN_PLUGIN_GROUP_ID && artifactId == KotlinMavenImporter.KOTLIN_PLUGIN_ARTIFACT_ID

fun Element?.sourceDirectories(): List<String> =
    this?.getChildren(KotlinMavenImporter.KOTLIN_PLUGIN_SOURCE_DIRS_CONFIG)?.flatMap { it.children ?: emptyList() }?.map { it.textTrim }
        ?: emptyList()

private fun MavenPlugin.Execution.sourceType() =
    goals.map { if (isTestGoalName(it)) SourceType.TEST else SourceType.PROD }
        .distinct()
        .singleOrNull() ?: SourceType.PROD

private fun isTestGoalName(goalName: String) = goalName.startsWith("test-")

enum class SourceType {
    PROD, TEST
}