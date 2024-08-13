// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.ArrayUtil
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator
import org.jetbrains.idea.maven.importing.MavenWorkspaceFacetConfigurator
import org.jetbrains.idea.maven.importing.workspaceModel.getSourceRootUrls
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.jps.model.serialization.SerializationConstants
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.platforms.IdePlatformKindProjectStructure
import org.jetbrains.kotlin.idea.base.util.substringAfterLastOrNull
import org.jetbrains.kotlin.idea.compiler.configuration.*
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.workspaceModel.*
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.isKotlinNative
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME
import java.util.stream.Stream
import kotlin.streams.asStream

class KotlinMavenImporterEx : KotlinMavenImporter(), MavenWorkspaceFacetConfigurator {
    override fun isFacetDetectionDisabled(project: Project): Boolean {
        return false
    }

    override fun getAdditionalFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<MavenWorkspaceConfigurator.AdditionalFolder> {
        return collectSourceDirectories(context.mavenProject)
            .map {
                val type =
                    if (it.first == SourceType.PROD) MavenWorkspaceConfigurator.FolderType.SOURCE else MavenWorkspaceConfigurator.FolderType.TEST_SOURCE
                MavenWorkspaceConfigurator.AdditionalFolder(it.second, type)
            }.asSequence().asStream()
    }

    private fun createWorkspaceEntity(module: ModuleEntity): KotlinSettingsEntity.Builder =
        KotlinSettingsEntity(
            KotlinFacetType.INSTANCE.presentableName,
            module.symbolicId,
            emptyList(),
            emptyList(),
            true,
            emptyList(),
            emptyList(),
            emptySet(),
            emptyList(),
            false,
            "",
            false,
            emptyList(),
            KotlinModuleKind.DEFAULT,
            emptyList(),
            KotlinFacetSettings.CURRENT_VERSION,
            false,
            module.entitySource
        )

    override fun preProcess(
        storage: MutableEntityStorage,
        module: ModuleEntity,
        project: Project,
        mavenProject: MavenProject
    ) {
        if (!isMigratedToConfigurator) return
        storage.modifyModuleEntity(module) {
            this.kotlinSettings += createWorkspaceEntity(module)
        }

        val mavenPlugin = mavenProject.findKotlinMavenPlugin() ?: return
        val currentVersion = mavenPlugin.compilerVersion
        val accumulatorVersion = project.getUserData(KOTLIN_JPS_VERSION_ACCUMULATOR)
        project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, maxOf(accumulatorVersion ?: currentVersion, currentVersion))
    }

    override fun process(
        storage: MutableEntityStorage,
        module: ModuleEntity,
        project: Project,
        mavenProject: MavenProject
    ) {
        if (!isMigratedToConfigurator) return

        val moduleName = module.name
        val sourceRoots = ArrayUtil.toStringArray(module.getSourceRootUrls(false))
        val mavenPlugin = mavenProject.findKotlinMavenPlugin() ?: return
        val compilerVersion = mavenPlugin.compilerVersion
        val configuration = mavenPlugin.configurationElement
        val platform = detectPlatform(mavenProject)?.defaultPlatform

        //detect version
        project.getUserData(KOTLIN_JPS_VERSION_ACCUMULATOR)?.let { version ->
            KotlinJpsPluginSettings.importKotlinJpsVersionFromExternalBuildSystem(
                project,
                version.rawVersion,
                isDelegatedToExtBuild = MavenRunner.getInstance(project).settings.isDelegateBuildToMaven,
                externalSystemId = SerializationConstants.MAVEN_EXTERNAL_SOURCE_ID
            )

            project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, null)
        }

        val kotlinFacetSettings = KotlinFacetSettings()

        kotlinFacetSettings.useProjectSettings = false

        // configure facet
        LOG.debug("Configuring facet")
        with(kotlinFacetSettings) {
            this.compilerArguments = null
            this.targetPlatform = null
            this.compilerSettings = null
            this.isHmppEnabled = false
            this.dependsOnModuleNames = emptyList()
            this.additionalVisibleModuleNames = emptySet()


            // initialize
            val shouldInferLanguageLevel = languageLevel == null
            val shouldInferAPILevel = apiLevel == null
            if (compilerSettings == null) {
                compilerSettings = KotlinCompilerSettings.getInstance(project).settings
            }

            val commonArguments = KotlinCommonCompilerArgumentsHolder.getInstance(project).settings
            if (compilerArguments == null) {
                val targetPlatform = platform ?: getDefaultTargetPlatform(project)
                LOG.debug("Detected target platform ", targetPlatform)

                val argumentsForPlatform = IdePlatformKindProjectStructure.getInstance(project)
                    .getCompilerArguments(targetPlatform.idePlatformKind)

                compilerArguments = targetPlatform.createArguments {
                    if (argumentsForPlatform != null) {
                        when {
                            argumentsForPlatform is K2JVMCompilerArguments &&
                                    this is K2JVMCompilerArguments -> copyK2JVMCompilerArguments(argumentsForPlatform, this)

                            argumentsForPlatform is K2JSCompilerArguments &&
                                    this is K2JSCompilerArguments -> copyK2JSCompilerArguments(argumentsForPlatform, this)

                            else -> error("Unsupported copy arguments combination: ${argumentsForPlatform.javaClass.name} and ${javaClass.name}")
                        }
                    }

                    copyCommonCompilerArguments(commonArguments, this)
                }

                this.targetPlatform = targetPlatform
            }

            if (shouldInferLanguageLevel) {
                languageLevel = (if (useProjectSettings) LanguageVersion.fromVersionString(commonArguments.languageVersion) else null)
                    ?: getDefaultLanguageLevel(compilerVersion, coerceRuntimeLibraryVersionToReleased = false)
                LOG.debug("Inferred languageLevel to ", languageLevel)
            }

            if (shouldInferAPILevel) {
                apiLevel = if (useProjectSettings) {
                    LanguageVersion.fromVersionString(commonArguments.apiVersion) ?: languageLevel
                } else if (targetPlatform?.idePlatformKind?.isKotlinNative == true) {
                    languageLevel?.coerceAtMostVersion(compilerVersion)
                } else {
                    val minVersion =
                        module.dependencies.mapNotNull { moduleDependencyItem ->
                            if (moduleDependencyItem !is LibraryDependency) return@mapNotNull null
                            val name = moduleDependencyItem.library.name
                            val artifactWithVersion = name.substringAfterLastOrNull("org.jetbrains.kotlin:")
                            if (artifactWithVersion != null && artifactWithVersion.contains(KOTLIN_JAVA_STDLIB_NAME)) {
                                return@mapNotNull artifactWithVersion.substringAfterLastOrNull(":")
                            } else null
                        }.minOrNull()
                    if (minVersion != null) {
                        getDefaultVersion(IdeKotlinVersion.parse(minVersion).getOrNull(), false).languageVersion
                    } else {
                        languageLevel
                    }
                }
                LOG.debug("Inferred apiLevel to ", apiLevel)
            }
            // end of initialize
            this.pureKotlinSourceFolders = pureKotlinSourceFolders
        }
        // end of facet configuration

        // setup arguments
        val configuredPlatform = kotlinFacetSettings.targetPlatform!!
        val sharedArguments = getCompilerArgumentsByConfigurationElement(mavenProject, configuration, configuredPlatform, project)
        val executionArguments = mavenPlugin.executions
            ?.firstOrNull { it.goals.any { s -> s in compilationGoals } }
            ?.configurationElement?.let { getCompilerArgumentsByConfigurationElement(mavenProject, it, configuredPlatform, project) }
        LOG.debug("Parsing compiler arguments: ", sharedArguments.args)
        parseCompilerArgumentsToFacetSettings(sharedArguments.args, kotlinFacetSettings, null) //modifiableModelsProvider -> null
        if (executionArguments != null) {
            parseCompilerArgumentsToFacetSettings(executionArguments.args, kotlinFacetSettings, null) //modifiableModelsProvider -> null
        }
        if (kotlinFacetSettings.compilerArguments is K2JSCompilerArguments) {
            deprecatedKotlinJsCompiler(project, compilerVersion.kotlinVersion)
        }

        MavenProjectImportHandler.getInstances(project).forEach { it(kotlinFacetSettings, mavenProject) }
        setImplementedModuleName(kotlinFacetSettings, mavenProject, project)
        kotlinFacetSettings.noVersionAutoAdvance()

        if ((sharedArguments.jvmTarget6IsUsed || executionArguments?.jvmTarget6IsUsed == true) &&
            project.getUserData(KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED) != true
        ) {
            project.putUserData(KOTLIN_JVM_TARGET_6_NOTIFICATION_DISPLAYED, true)
            displayJvmTarget6UsageNotification(project)
        }

        val kotlinSettingsEntity = storage.entities(KotlinSettingsEntity::class.java).first { it.module.name == moduleName }
        storage.modifyKotlinSettingsEntity(kotlinSettingsEntity) {
            this.sourceRoots = sourceRoots.toMutableList()
            this.useProjectSettings = kotlinFacetSettings.useProjectSettings
            this.implementedModuleNames = kotlinFacetSettings.implementedModuleNames.toMutableList()
            this.dependsOnModuleNames = kotlinFacetSettings.dependsOnModuleNames.toMutableList()
            this.additionalVisibleModuleNames = kotlinFacetSettings.additionalVisibleModuleNames.toMutableSet()
            this.productionOutputPath = kotlinFacetSettings.productionOutputPath
            this.testOutputPath = kotlinFacetSettings.testOutputPath
            this.sourceSetNames = kotlinFacetSettings.sourceSetNames.toMutableList()
            this.isTestModule = kotlinFacetSettings.isTestModule
            this.externalProjectId = "Maven"
            this.isHmppEnabled = kotlinFacetSettings.isHmppEnabled
            this.pureKotlinSourceFolders = kotlinFacetSettings.pureKotlinSourceFolders.toMutableList()
            this.kind = kotlinFacetSettings.kind
            this.compilerArguments = CompilerArgumentsSerializer.serializeToString(kotlinFacetSettings.compilerArguments)
            this.compilerSettings = kotlinFacetSettings.compilerSettings?.let {
                CompilerSettingsData(
                    it.additionalArguments,
                    it.scriptTemplates,
                    it.scriptTemplatesClasspath,
                    it.copyJsLibraryFiles,
                    it.outputDirectoryForJsLibraryFiles
                )
            }
            this.targetPlatform = kotlinFacetSettings.targetPlatform?.serializeComponentPlatforms()
        }
    }

    override fun isMigratedToConfigurator(): Boolean {
        return KotlinFacetBridgeFactory.kotlinFacetBridgeEnabled
    }

    private fun getDefaultTargetPlatform(project: Project): TargetPlatform {
        val jvmTarget =
            Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings.jvmTarget?.let { JvmTarget.fromString(it) } ?: JvmTarget.JVM_1_8
        return JvmPlatforms.jvmPlatformByTargetVersion(jvmTarget)
    }

    companion object {
        private val LOG = logger<KotlinMavenImporterEx>()
    }
}