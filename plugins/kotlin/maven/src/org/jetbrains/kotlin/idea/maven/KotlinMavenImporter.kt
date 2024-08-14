// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.maven

import com.intellij.externalSystem.ImportedLibraryType
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.util.PairConsumer
import com.intellij.util.PathUtil
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.importing.MavenImporter
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter
import org.jetbrains.idea.maven.model.MavenPlugin
import org.jetbrains.idea.maven.project.*
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.SerializationConstants
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.compilerRunner.ArgumentUtils
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.tooling
import org.jetbrains.kotlin.idea.base.platforms.detectLibraryKind
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.configureFacet
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.facet.noVersionAutoAdvance
import org.jetbrains.kotlin.idea.facet.parseCompilerArgumentsToFacet
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
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
import java.util.*

interface MavenProjectImportHandler {
    companion object : ProjectExtensionDescriptor<MavenProjectImportHandler>(
        "org.jetbrains.kotlin.mavenProjectImportHandler",
        MavenProjectImportHandler::class.java
    )

    operator fun invoke(facetSettings: IKotlinFacetSettings, mavenProject: MavenProject)
}

open class KotlinMavenImporter : MavenImporter(KOTLIN_PLUGIN_GROUP_ID, KOTLIN_PLUGIN_ARTIFACT_ID) {
    companion object {
        const val KOTLIN_PLUGIN_GROUP_ID = "org.jetbrains.kotlin"
        const val KOTLIN_PLUGIN_ARTIFACT_ID = "kotlin-maven-plugin"

        const val KOTLIN_PLUGIN_SOURCE_DIRS_CONFIG = "sourceDirs"

        private val LOG = logger<KotlinMavenImporter>()

        internal val KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED = Key<Boolean>("KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED")
        val KOTLIN_JPS_VERSION_ACCUMULATOR = Key<IdeKotlinVersion>("KOTLIN_JPS_VERSION_ACCUMULATOR")
    }

    override fun preProcess(
        module: Module,
        mavenProject: MavenProject,
        changes: MavenProjectChanges,
        modifiableModelsProvider: IdeModifiableModelsProvider
    ) {
        module.project.putUserData(KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED, null)
        module.project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, null)
    }

    override fun process(
      modifiableModelsProvider: IdeModifiableModelsProvider,
      module: Module,
      rootModel: MavenRootModelAdapter,
      mavenModel: MavenProjectsTree,
      mavenProject: MavenProject,
      changes: MavenProjectChanges,
      mavenProjectToModuleName: MutableMap<MavenProject, String>,
      postTasks: MutableList<MavenProjectsProcessorTask>
    ) {

        if (changes.hasPluginsChanges()) {
            if (module.name == mavenProjectToModuleName[mavenProject]) {
                contributeSourceDirectories(mavenProject, module, rootModel)
            }
        }

        val mavenPlugin = mavenProject.findKotlinMavenPlugin() ?: return
        val currentVersion = mavenPlugin.compilerVersion
        val accumulatorVersion = module.project.getUserData(KOTLIN_JPS_VERSION_ACCUMULATOR)
        module.project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, maxOf(accumulatorVersion ?: currentVersion, currentVersion))
    }

    override fun postProcess(
        module: Module,
        mavenProject: MavenProject,
        changes: MavenProjectChanges,
        modifiableModelsProvider: IdeModifiableModelsProvider
    ) {
        super.postProcess(module, mavenProject, changes, modifiableModelsProvider)
        val project = module.project
        project.getUserData(KOTLIN_JPS_VERSION_ACCUMULATOR)?.let { version ->
            KotlinJpsPluginSettings.importKotlinJpsVersionFromExternalBuildSystem(
                project,
                version.rawVersion,
                isDelegatedToExtBuild = MavenRunner.getInstance(project).settings.isDelegateBuildToMaven,
                externalSystemId = SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
            )

            project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, null)
        }

        if (changes.hasDependencyChanges()) {
            scheduleDownloadStdlibSources(mavenProject, module)

            val targetLibraryKind = detectPlatformByExecutions(mavenProject)?.tooling?.libraryKind
            if (targetLibraryKind != null) {
                modifiableModelsProvider.getModifiableRootModel(module).orderEntries().forEachLibrary { library ->
                    val libraryKind = (library as LibraryEx).kind
                    if (libraryKind == null || libraryKind == ImportedLibraryType.IMPORTED_LIBRARY_KIND) {
                        val model = modifiableModelsProvider.getModifiableLibraryModel(library) as LibraryEx.ModifiableModelEx
                        detectLibraryKind(library, project)?.let { model.kind = it }
                    }
                    true
                }
            }
        }

        configureFacet(mavenProject, modifiableModelsProvider, module)
    }

    override fun collectSourceRoots(mavenProject: MavenProject, result: PairConsumer<String, JpsModuleSourceRootType<*>>) {
        val directories = collectSourceDirectories(mavenProject)
        for ((type, dir) in directories) {
            val jpsType: JpsModuleSourceRootType<*> = when (type) {
                SourceType.TEST -> JavaSourceRootType.TEST_SOURCE
                SourceType.PROD -> JavaSourceRootType.SOURCE
            }
            result.consume(dir, jpsType)
        }
    }

    protected fun scheduleDownloadStdlibSources(mavenProject: MavenProject, module: Module) {
        // TODO: here we have to process all kotlin libraries but for now we only handle standard libraries
        val artifacts = mavenProject.dependencyArtifactIndex.data[KOTLIN_PLUGIN_GROUP_ID]?.values
            ?.flatMap { it.filter { artifact -> artifact.isResolved } } ?: emptyList()

        val libraryNames = mutableSetOf<String?>()
        OrderEnumerator.orderEntries(module).forEachLibrary { library ->
            val model = library.modifiableModel
            try {
                if (model.getFiles(OrderRootType.SOURCES).isEmpty()) {
                    libraryNames.add(library.name)
                }
            } finally {
                Disposer.dispose(model)
            }
            true
        }
        val toBeDownloaded = artifacts.filter { it.libraryName in libraryNames }

        if (toBeDownloaded.isNotEmpty()) {
            val manager = MavenProjectsManager.getInstance(module.project)
            manager.scheduleDownloadArtifacts(listOf(mavenProject), toBeDownloaded, true, false)
        }
    }

    protected fun configureJSOutputPaths(
        mavenProject: MavenProject,
        modifiableRootModel: ModifiableRootModel,
        facetSettings: IKotlinFacetSettings,
        mavenPlugin: MavenPlugin
    ) {
        fun parentPath(path: String): String =
            File(path).absoluteFile.parentFile.absolutePath

        val sharedOutputFile = mavenPlugin.configurationElement?.getChild("outputFile")?.text

        val compilerModuleExtension = modifiableRootModel.getModuleExtension(CompilerModuleExtension::class.java) ?: return
        val buildDirectory = mavenProject.buildDirectory

        val executions = mavenPlugin.executions

        executions.forEach {
            val explicitOutputFile = it.configurationElement?.getChild("outputFile")?.text ?: sharedOutputFile
            if (PomFile.KotlinGoals.Js in it.goals) {
                // see org.jetbrains.kotlin.maven.K2JSCompilerMojo
                val outputFilePath =
                    PathUtil.toSystemDependentName(explicitOutputFile ?: "$buildDirectory/js/${mavenProject.mavenId.artifactId}.js")
                compilerModuleExtension.setCompilerOutputPath(parentPath(outputFilePath))
                facetSettings.productionOutputPath = outputFilePath
            }
            if (PomFile.KotlinGoals.TestJs in it.goals) {
                // see org.jetbrains.kotlin.maven.KotlinTestJSCompilerMojo
                val outputFilePath = PathUtil.toSystemDependentName(
                    explicitOutputFile ?: "$buildDirectory/test-js/${mavenProject.mavenId.artifactId}-tests.js"
                )
                compilerModuleExtension.setCompilerOutputPathForTests(parentPath(outputFilePath))
                facetSettings.testOutputPath = outputFilePath
            }
        }
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
                val jpsVersion = KotlinJpsPluginSettings.getInstance(project)?.settings?.version
                LOG.debug("Found JPS version ", jpsVersion)

                if (jvmTarget == JvmTarget.JVM_1_6.description && jpsVersion?.isBlank() != false) {
                    // Load JVM target 1.6 in Maven projects as 1.8, for IDEA platforms <= 222.
                    // The reason is that JVM target 1.6 is no longer supported by the latest Kotlin compiler, yet we'd like JPS projects imported from
                    // Maven to be compilable by IDEA, to avoid breaking local development.
                    // (Since IDEA 222, JPS plugin is unbundled from the Kotlin IDEA plugin, so this change is not needed there in case
                    // when explicit version is specified in kotlinc.xml)
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

    protected val compilationGoals = listOf(
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

    private fun configureFacet(mavenProject: MavenProject, modifiableModelsProvider: IdeModifiableModelsProvider, module: Module) {
        val mavenPlugin = mavenProject.findKotlinMavenPlugin() ?: return
        val compilerVersion = mavenPlugin.compilerVersion

        val kotlinFacet = module.getOrCreateFacet(
            modifiableModelsProvider,
            false,
            SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
        )

        // TODO There should be a way to figure out the correct platform version
        val platform = detectPlatform(mavenProject)?.defaultPlatform

        kotlinFacet.configureFacet(compilerVersion, platform, modifiableModelsProvider)

        val facetSettings = kotlinFacet.configuration.settings
        val configuredPlatform = kotlinFacet.configuration.settings.targetPlatform!!
        val configuration = mavenPlugin.configurationElement
        val sharedArguments = getCompilerArgumentsByConfigurationElement(mavenProject, configuration, configuredPlatform, module.project)
        val executionArguments = mavenPlugin.executions
            ?.firstOrNull { it.goals.any { s -> s in compilationGoals } }
            ?.configurationElement?.let { getCompilerArgumentsByConfigurationElement(mavenProject, it, configuredPlatform, module.project) }
        parseCompilerArgumentsToFacet(sharedArguments.args, kotlinFacet, modifiableModelsProvider)
        if (executionArguments != null) {
            parseCompilerArgumentsToFacet(executionArguments.args, kotlinFacet, modifiableModelsProvider)
        }
        if (facetSettings.compilerArguments is K2JSCompilerArguments) {
            configureJSOutputPaths(mavenProject, modifiableModelsProvider.getModifiableRootModel(module), facetSettings, mavenPlugin)
            deprecatedKotlinJsCompiler(module.project, compilerVersion.kotlinVersion)
        }

        MavenProjectImportHandler.getInstances(module.project).forEach { it(kotlinFacet.configuration.settings, mavenProject) }
        setImplementedModuleName(facetSettings, mavenProject, module.project)
        facetSettings.noVersionAutoAdvance()

        if ((sharedArguments.jvmTarget6IsUsed || executionArguments?.jvmTarget6IsUsed == true) &&
            module.project.getUserData(KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED) != true
        ) {
            module.project.putUserData(KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED, true)
            displayJvmTarget6UsageNotification(module.project)
        }
    }

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

    protected fun detectPlatform(mavenProject: MavenProject) =
        detectPlatformByExecutions(mavenProject) ?: detectPlatformByLibraries(mavenProject)

    protected fun detectPlatformByExecutions(mavenProject: MavenProject): IdePlatformKind? {
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

    // TODO in theory it should work like this but it doesn't as it couldn't unmark source roots that are not roots anymore.
    //     I believe this is something should be done by the underlying maven importer implementation or somewhere else in the IDEA
    //     For now there is a contributeSourceDirectories implementation that deals with the issue
    //        see https://youtrack.jetbrains.com/issue/IDEA-148280

    //    override fun collectSourceRoots(mavenProject: MavenProject, result: PairConsumer<String, JpsModuleSourceRootType<*>>) {
    //        for ((type, dir) in collectSourceDirectories(mavenProject)) {
    //            val jpsType: JpsModuleSourceRootType<*> = when (type) {
    //                SourceType.PROD -> JavaSourceRootType.SOURCE
    //                SourceType.TEST -> JavaSourceRootType.TEST_SOURCE
    //            }
    //
    //            result.consume(dir, jpsType)
    //        }
    //    }

    private fun contributeSourceDirectories(mavenProject: MavenProject, module: Module, rootModel: MavenRootModelAdapter) {
        val directories = collectSourceDirectories(mavenProject)

        val toBeAdded = directories.map { it.second }.toSet()
        val state = module.kotlinImporterComponent

        val isNonJvmModule = mavenProject
            .plugins
            .asSequence()
            .filter { it.isKotlinPlugin() }
            .flatMap { it.executions.asSequence() }
            .flatMap { it.goals.asSequence() }
            .any { it in PomFile.KotlinGoals.CompileGoals && it !in PomFile.KotlinGoals.JvmGoals }

        val prodSourceRootType: JpsModuleSourceRootType<*> = if (isNonJvmModule) SourceKotlinRootType else JavaSourceRootType.SOURCE
        val testSourceRootType: JpsModuleSourceRootType<*> =
            if (isNonJvmModule) TestSourceKotlinRootType else JavaSourceRootType.TEST_SOURCE

        for ((type, dir) in directories) {
            if (rootModel.getSourceFolder(File(dir)) == null) {
                val jpsType: JpsModuleSourceRootType<*> = when (type) {
                    SourceType.TEST -> testSourceRootType
                    SourceType.PROD -> prodSourceRootType
                }

                rootModel.addSourceFolder(dir, jpsType)
            }
        }

        if (isNonJvmModule) {
            mavenProject.sources.forEach { rootModel.addSourceFolder(it, SourceKotlinRootType) }
            mavenProject.testSources.forEach { rootModel.addSourceFolder(it, TestSourceKotlinRootType) }
            mavenProject.resources.forEach { rootModel.addSourceFolder(it.directory, ResourceKotlinRootType) }
            mavenProject.testResources.forEach { rootModel.addSourceFolder(it.directory, TestResourceKotlinRootType) }
            KotlinSdkType.setUpIfNeeded()
        }

        state.addedSources.filter { it !in toBeAdded }.forEach {
            rootModel.unregisterAll(it, true, true)
            state.addedSources.remove(it)
        }
        state.addedSources.addAll(toBeAdded)
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

fun MavenPlugin.isKotlinPlugin() =
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

@State(
    name = "AutoImportedSourceRoots",
    storages = [(Storage(StoragePathMacros.MODULE_FILE))]
)
class KotlinImporterComponent : PersistentStateComponent<KotlinImporterComponent.State> {
    class State(var directories: List<String> = ArrayList())

    val addedSources: MutableSet<String> = Collections.synchronizedSet(HashSet())

    override fun loadState(state: State) {
        addedSources.clear()
        addedSources.addAll(state.directories)
    }

    override fun getState(): State {
        return State(addedSources.sorted())
    }
}

internal val Module.kotlinImporterComponent: KotlinImporterComponent
    get() = getService(KotlinImporterComponent::class.java) ?: error("Service ${KotlinImporterComponent::class.java} not found")
