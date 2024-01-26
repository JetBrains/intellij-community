// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.wizard.service

import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RemoteRepositoryDescription
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.kotlin.config.VersionView
import org.jetbrains.kotlin.config.apiVersionView
import org.jetbrains.kotlin.config.languageVersionView
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle.Companion.INSTANCE
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.service.ProjectImportingWizardService
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.JvmModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.inContextOfModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemSettings
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType
import org.jetbrains.kotlin.tools.projectWizard.wizard.IdeWizard
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder
import java.nio.file.Path
import com.intellij.openapi.module.Module as IdeaModule

class IdeaJpsWizardService(
    private val project: Project,
    private val modulesModel: ModifiableModuleModel,
    private val modulesBuilder: NewProjectWizardModuleBuilder,
    private val ideWizard: IdeWizard,
) : ProjectImportingWizardService, IdeaWizardService {
    override fun isSuitableFor(buildSystemType: BuildSystemType): Boolean =
        buildSystemType == BuildSystemType.Jps

    override fun importProject(
        reader: Reader,
        path: Path,
        modulesIrs: List<ModuleIR>,
        buildSystem: BuildSystemType,
        buildSystemSettings: BuildSystemSettings?
    ): TaskResult<Unit> {
        KotlinSdkType.setUpIfNeeded()
        val projectImporter = ProjectImporter(project, modulesModel, path, modulesIrs, ideWizard.jdk)
        modulesBuilder.addModuleConfigurationUpdater(
            JpsModuleConfigurationUpdater(
                ideWizard.jpsData,
                projectImporter,
                project,
                reader,
                ideWizard.isCreatingNewProject,
                ideWizard.projectName,
                ideWizard.stdlibForJps
            )
        )

        projectImporter.import()
        Disposer.dispose(ideWizard.jpsData.libraryOptionsPanel)
        return UNIT_SUCCESS
    }
}

private class JpsModuleConfigurationUpdater(
    private val jpsData: IdeWizard.JpsData,
    private val projectImporter: ProjectImporter,
    private val project: Project,
    private val reader: Reader,
    private val isCreatingProject: Boolean,
    private val newProjectOrModuleName: String?,
    private val kotlinStdlib: LibraryOrderEntry? = null
) : ModuleBuilder.ModuleConfigurationUpdater() {

    // All modules come to this function
    override fun update(module: IdeaModule, rootModel: ModifiableRootModel) = with(jpsData) {
        if (isCreatingProject) {
            addKotlinJavaRuntime(rootModel)
        } else if (newProjectOrModuleName == module.name) {
            if (kotlinStdlib != null) {
                rootModel.addOrderEntry(kotlinStdlib)
            } else {
                addKotlinJavaRuntime(rootModel)
            }
        }
        libraryDescription.finishLibConfiguration(module, rootModel, isCreatingProject)
        setUpJvmTargetVersionForModules()
        updateKotlinCommonCompilerArguments(module.project)
        if (isCreatingProject) {
            ProjectCodeStyleImporter.apply(module.project, INSTANCE)
        }
    }

    private fun IdeWizard.JpsData.addKotlinJavaRuntime(rootModel: ModifiableRootModel) {
        libraryOptionsPanel.apply()?.addLibraries(
            rootModel,
            ArrayList(),
            librariesContainer
        )
    }

     /* On creating new module, each module of the project calls this function.
     Probably, this function should have been called only once with
     `modules = projectImporter.modulesIrs` being all modules. In fact, it's vice versa:
     1. This function is called for each module in the project.
     2. `modules = projectImporter.modulesIrs` is always this one new module that we create. */
    private fun setUpJvmTargetVersionForModules() {
        // `modules` is always this one new module that we create
        val modules = projectImporter.modulesIrs
        val newModule = modules.first()
        val kotlin2JvmCompilerArgumentsHolder = Kotlin2JvmCompilerArgumentsHolder.getInstance(project)
        if (kotlin2JvmCompilerArgumentsHolder.settings.jvmTarget == null) {
            kotlin2JvmCompilerArgumentsHolder.update {
               jvmTarget = newModule.jvmTarget().value
           }
        }
    }

    private fun updateKotlinCommonCompilerArguments(project: Project) {
        val kotlinCommonCompilerArgumentsHolder = KotlinCommonCompilerArgumentsHolder.getInstance(project)
        val bundledLanguageVersion = KotlinPluginLayout.standaloneCompilerVersion.languageVersion

        if (kotlinCommonCompilerArgumentsHolder.settings.languageVersion == null) {
            kotlinCommonCompilerArgumentsHolder.update {
                languageVersionView = VersionView.Specific(bundledLanguageVersion)
            }
        }
        if (kotlinCommonCompilerArgumentsHolder.settings.apiVersion == null) {
            kotlinCommonCompilerArgumentsHolder.update {
                apiVersionView = VersionView.Specific(bundledLanguageVersion)
            }
        }
    }

    private fun ModuleIR.jvmTarget() = reader {
        inContextOfModuleConfigurator(originalModule) {
            JvmModuleConfigurator.targetJvmVersion.reference.settingValue
        }
    }

}

private class ProjectImporter(
    private val project: Project,
    private val modulesModel: ModifiableModuleModel,
    private val path: Path,
    val modulesIrs: List<ModuleIR>,
    val sdk: Sdk?
) {
    fun import() = modulesIrs.mapSequence { moduleIR ->
        convertModule(moduleIR).map { moduleIR to it }
    }.map { irsToIdeaModule ->
        val irsToIdeaModuleMap = irsToIdeaModule.associate { (ir, module) -> ir.name to module }
        irsToIdeaModule.forEach { (moduleIr, ideaModule) ->
            addModuleDependencies(moduleIr, ideaModule, irsToIdeaModuleMap)
        }
    } andThen safe { runWriteAction { modulesModel.commit() } }

    private fun convertModule(moduleIr: ModuleIR): TaskResult<IdeaModule> {
        val module = modulesModel.newModule(
            (moduleIr.path / "${moduleIr.name}.iml").toString(),
            ModuleTypeId.JAVA_MODULE
        )
        val rootModel = ModuleRootManager.getInstance(module).modifiableModel
        val contentRoot = rootModel.addContentEntry(moduleIr.path.url)
        moduleIr.sourcesets.forEach { sourceset ->
            val isTest = sourceset.sourcesetType == SourcesetType.test
            sourceset.sourcePaths.forEach { (sourceType, path) ->
                val pathType = when {
                  isTest && sourceType == SourcesetSourceType.RESOURCES -> JavaResourceRootType.TEST_RESOURCE
                    sourceType == SourcesetSourceType.RESOURCES -> JavaResourceRootType.RESOURCE
                    isTest -> JavaSourceRootType.TEST_SOURCE
                    else -> JavaSourceRootType.SOURCE
                }
                contentRoot.addSourceFolder(path.url, pathType)
            }
        }

        if (sdk != null) {
            rootModel.setSdk(sdk)
        } else {
            rootModel.inheritSdk()
        }
        runWriteAction { rootModel.commit() }
        addLibrariesToTheModule(moduleIr, module)
        return Success(module)
    }

    private fun addLibrariesToTheModule(moduleIr: ModuleIR, module: IdeaModule) {
        moduleIr.irs.forEach { ir ->
            if (ir is LibraryDependencyIR && !ir.isKotlinStdlib) {
                attachLibraryToModule(ir, module)
            }
        }
    }

    private fun addModuleDependencies(
        moduleIr: ModuleIR,
        module: com.intellij.openapi.module.Module,
        moduleNameToIdeaModuleMap: Map<String, IdeaModule>
    ) {
        moduleIr.irs.forEach { ir ->
            if (ir is ModuleDependencyIR) {
                attachModuleDependencyToModule(ir, module, moduleNameToIdeaModuleMap)
            }
        }
    }

    private fun attachModuleDependencyToModule(
        moduleDependency: ModuleDependencyIR,
        module: IdeaModule,
        moduleNameToIdeaModuleMap: Map<String, IdeaModule>
    ) {
        val dependencyName = moduleDependency.path.parts.lastOrNull() ?: return
        val dependencyModule = moduleNameToIdeaModuleMap[dependencyName] ?: return
        ModuleRootModificationUtil.addDependency(module, dependencyModule)
    }

    private fun attachLibraryToModule(
        libraryDependency: LibraryDependencyIR,
        module: IdeaModule
    ) {
        val artifact = libraryDependency.artifact as? MavenArtifact ?: return
        val (classesRoots, sourcesRoots) = downloadLibraryAndGetItsClasses(libraryDependency, artifact)

        ModuleRootModificationUtil.addModuleLibrary(
            module,
            if (classesRoots.size > 1) artifact.artifactId else null,
            classesRoots,
            sourcesRoots,
            emptyList(),
            when (libraryDependency.dependencyType) {
                DependencyType.MAIN -> DependencyScope.COMPILE
                DependencyType.TEST -> DependencyScope.TEST
            },
            false
        ) {
            it.kind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
            it.properties = RepositoryLibraryProperties(artifact.groupId, artifact.artifactId, libraryDependency.version.text)
        }
    }

    private fun downloadLibraryAndGetItsClasses(
        libraryDependency: LibraryDependencyIR,
        artifact: MavenArtifact
    ): LibraryClassesAndSources {
        val libraryProperties = RepositoryLibraryProperties(
            artifact.groupId,
            artifact.artifactId,
            libraryDependency.version.toString()
        )
        val orderRoots = JarRepositoryManager.loadDependenciesModal(
            project,
            libraryProperties,
            true,
            true,
            null,
            artifact.repositories.map { it.asJPSRepository() }
        )

        return LibraryClassesAndSources.fromOrderRoots(orderRoots)
    }

    private fun Repository.asJPSRepository() = RemoteRepositoryDescription(
        idForMaven,
        idForMaven,
        url
    )

    private data class LibraryClassesAndSources(
        val classes: List<String>,
        val sources: List<String>
    ) {
        companion object {
            fun fromOrderRoots(orderRoots: Collection<OrderRoot>) = LibraryClassesAndSources(
                orderRoots.filterRootTypes(OrderRootType.CLASSES),
                orderRoots.filterRootTypes(OrderRootType.SOURCES)
            )

            private fun Collection<OrderRoot>.filterRootTypes(rootType: OrderRootType) =
                filter { it.type == rootType }
                    .mapNotNull { PathUtil.getLocalPath(it.file) }
                    .let(OrderEntryFix::refreshAndConvertToUrls)

        }
    }
}

private val Path.url
    get() = VfsUtil.pathToUrl(toString())
