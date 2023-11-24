// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.externalSystem.project.PackagingModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.packaging.artifacts.ModifiableArtifactModel
import com.intellij.platform.workspace.jps.entities.ModuleDependencyItem
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.ArrayUtil
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.importing.MavenWorkspaceConfigurator

import org.jetbrains.idea.maven.importing.MavenWorkspaceFacetConfigurator
import org.jetbrains.idea.maven.importing.workspaceModel.getSourceRootUrls
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsProcessorTask
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.platforms.IdePlatformKindProjectStructure
import org.jetbrains.kotlin.idea.base.util.substringAfterLastOrNull
import org.jetbrains.kotlin.idea.compiler.configuration.*
import org.jetbrains.kotlin.idea.facet.*
import org.jetbrains.kotlin.idea.workspaceModel.*
import org.jetbrains.kotlin.idea.workspaceModel.CompilerSettingsData
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.impl.isKotlinNative
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.utils.PathUtil.KOTLIN_JAVA_STDLIB_NAME
import java.util.stream.Stream

class KotlinMavenImporterEx : KotlinMavenImporter(), MavenWorkspaceFacetConfigurator {
    override fun isFacetDetectionDisabled(project: Project): Boolean {
        return false
    }

    override fun getAdditionalSourceFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
        return collectSourceDirectories(context.mavenProject).filter {
            it.first == SourceType.PROD
        }.map { it.second }.stream()
    }

    override fun getAdditionalTestSourceFolders(context: MavenWorkspaceConfigurator.FoldersContext): Stream<String> {
        return collectSourceDirectories(context.mavenProject).filter {
            it.first == SourceType.TEST
        }.map { it.second }.stream()
    }

    private fun createWorkspaceEntity(module: ModuleEntity): KotlinSettingsEntity =
        KotlinSettingsEntity(
            KotlinFacetType.INSTANCE.presentableName,
            module.symbolicId,
            emptyList(),
            emptyList(),
            true,
            emptyList(),
            emptyList(),
            emptySet(),
            "",
            "",
            emptyList(),
            false,
            "",
            false,
            emptyList(),
            KotlinModuleKind.DEFAULT,
            "",
            CompilerSettingsData("", "", "", true, "lib", true), "", module.entitySource
        ) {
            this.module = module
        }

    override fun preProcess(
        storage: MutableEntityStorage,
        module: ModuleEntity,
        project: Project,
        mavenProject: MavenProject,
        artifactModel: ModifiableArtifactModel
    ) {
        if (!isMigratedToConfigurator) return
        storage.addEntity(createWorkspaceEntity(module))

        val mavenPlugin = mavenProject.findKotlinMavenPlugin() ?: return
        val currentVersion = mavenPlugin.compilerVersion
        val accumulatorVersion = project.getUserData(KOTLIN_JPS_VERSION_ACCUMULATOR)
        project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, maxOf(accumulatorVersion ?: currentVersion, currentVersion))
    }

    override fun process(
        storage: MutableEntityStorage,
        module: ModuleEntity,
        project: Project,
        mavenProject: MavenProject,
        mavenTree: MavenProjectsTree,
        mavenProjectToModuleName: Map<MavenProject, String>,
        packagingModel: PackagingModel,
        postTasks: MutableList<MavenProjectsProcessorTask>,
        userDataHolder: UserDataHolderEx
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
                isDelegatedToExtBuild = MavenRunner.getInstance(project).settings.isDelegateBuildToMaven
            )

            project.putUserData(KOTLIN_JPS_VERSION_ACCUMULATOR, null)
        }

        val kotlinFacetSettings = KotlinFacetSettings()

        kotlinFacetSettings.useProjectSettings = false

        // configure facet
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
            }

            if (shouldInferAPILevel) {
                apiLevel = if (useProjectSettings) {
                    LanguageVersion.fromVersionString(commonArguments.apiVersion) ?: languageLevel
                } else if (targetPlatform?.idePlatformKind?.isKotlinNative == true) {
                    languageLevel?.coerceAtMostVersion(compilerVersion)
                } else {
                    val minVersion =
                        module.dependencies.mapNotNull { moduleDependencyItem ->
                            if (moduleDependencyItem !is ModuleDependencyItem.Exportable.LibraryDependency) return@mapNotNull null
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
            }
            // end of initialize

            val apiLevel = apiLevel
            val languageLevel = languageLevel
            if (languageLevel != null && apiLevel != null && apiLevel > languageLevel) {
                this.apiLevel = languageLevel
            }
            this.pureKotlinSourceFolders = pureKotlinSourceFolders
        }
        // end of facet configuration

        // setup arguments
        val configuredPlatform = kotlinFacetSettings.targetPlatform!!
        val sharedArguments = getCompilerArgumentsByConfigurationElement(mavenProject, configuration, configuredPlatform, project)
        val executionArguments = mavenPlugin.executions
            ?.firstOrNull { it.goals.any { s -> s in compilationGoals } }
            ?.configurationElement?.let { getCompilerArgumentsByConfigurationElement(mavenProject, it, configuredPlatform, project) }
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
        storage.modifyEntity(kotlinSettingsEntity) {
            this.sourceRoots = sourceRoots.toMutableList()
            this.useProjectSettings = kotlinFacetSettings.useProjectSettings
            this.implementedModuleNames = kotlinFacetSettings.implementedModuleNames.toMutableList()
            this.dependsOnModuleNames = kotlinFacetSettings.dependsOnModuleNames.toMutableList()
            this.additionalVisibleModuleNames = kotlinFacetSettings.additionalVisibleModuleNames.toMutableSet()
            this.productionOutputPath = kotlinFacetSettings.productionOutputPath ?: ""
            this.testOutputPath = kotlinFacetSettings.testOutputPath ?: ""
            this.sourceSetNames = kotlinFacetSettings.sourceSetNames.toMutableList()
            this.isTestModule = kotlinFacetSettings.isTestModule
            this.externalProjectId = "Maven"
            this.isHmppEnabled = kotlinFacetSettings.isHmppEnabled
            this.pureKotlinSourceFolders = kotlinFacetSettings.pureKotlinSourceFolders.toMutableList()
            this.kind = kotlinFacetSettings.kind
            this.compilerArguments = KotlinModuleSettingsSerializer.serializeToString(kotlinFacetSettings.compilerArguments)
            val compilerSettings = kotlinFacetSettings.compilerSettings
            this.compilerSettings =
                if (compilerSettings == null) CompilerSettingsData("", "", "", true, "lib", false)
                else CompilerSettingsData(
                    compilerSettings.additionalArguments,
                    compilerSettings.scriptTemplates,
                    compilerSettings.scriptTemplatesClasspath,
                    compilerSettings.copyJsLibraryFiles,
                    compilerSettings.outputDirectoryForJsLibraryFiles,
                    true
                )
            this.targetPlatform = kotlinFacetSettings.targetPlatform?.serializeComponentPlatforms() ?: ""
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
}