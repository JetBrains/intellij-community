// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.facet.FacetManager
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryAddLibraryAction
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.psi.PsiElement
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryDescription
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.codeInsight.CliArgumentStringBuilder.replaceLanguageFeature
import org.jetbrains.kotlin.idea.base.platforms.StdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.util.findLibrary
import org.jetbrains.kotlin.idea.base.util.hasKotlinFilesInTestsOnly
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.ui.CreateLibraryDialogWithModules
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersionOrDefault
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.projectConfiguration.askUpdateRuntime
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread
import org.jetbrains.kotlin.idea.versions.forEachAllUsedLibraries
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class KotlinWithLibraryConfigurator<P : LibraryProperties<*>> protected constructor() : KotlinProjectConfigurator {
    protected abstract val libraryName: String

    protected abstract val messageForOverrideDialog: String

    protected abstract val dialogTitle: String

    abstract val libraryType: LibraryType<P>

    abstract val libraryJarDescriptor: LibraryJarDescriptor

    abstract val libraryProperties: P

    abstract val stdlibDetector: StdlibDetectorFacility

    override fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus {
        val module = moduleSourceRootGroup.baseModule
        if (!isApplicable(module)) {
            return ConfigureKotlinStatus.NON_APPLICABLE
        }
        if (isConfigured(module)) {
            return ConfigureKotlinStatus.CONFIGURED
        }
        return ConfigureKotlinStatus.CAN_BE_CONFIGURED
    }

    abstract fun isConfigured(module: Module): Boolean

    @JvmSuppressWildcards
    override fun configure(project: Project, excludeModules: Collection<Module>) {
        var nonConfiguredModules = if (!isUnitTestMode()) {
            underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread(
                project,
                progressTitle = KotlinProjectConfigurationBundle.message("lookup.modules.configurations.progress.text"),
                computable = { getCanBeConfiguredModules(project, this) }
            )
        } else {
            listOf(*ModuleManager.getInstance(project).modules)
        }
        nonConfiguredModules -= excludeModules

        var modulesToConfigure = nonConfiguredModules

        if (nonConfiguredModules.size > 1) {
            val dialog = CreateLibraryDialogWithModules(
                project, this,
                dialogTitle,
                excludeModules
            )

            if (!isUnitTestMode()) {
                dialog.show()
                if (!dialog.isOK) return
            } else {
                dialog.close(0)
            }

            modulesToConfigure = dialog.modulesToConfigure
        }


        val collector = NotificationMessageCollector.create(project)
        getOrCreateKotlinLibrary(project, collector)
        val writeActions = mutableListOf<() -> Unit>()
        ActionUtil.underModalProgress(project, KotlinProjectConfigurationBundle.message("configure.kotlin.in.modules.progress.text")) {
            val progressIndicator = ProgressManager.getGlobalProgressIndicator()
            for ((index, module) in modulesToConfigure.withIndex()) {
                if (!isUnitTestMode()) {
                    progressIndicator?.let {
                        it.checkCanceled()
                        it.fraction = index * 1.0 / modulesToConfigure.size
                        it.text = KotlinProjectConfigurationBundle.message("configure.kotlin.in.modules.progress.text")
                        it.text2 = KotlinProjectConfigurationBundle.message("configure.kotlin.in.module.0.progress.text", module.name)
                    }
                }
                configureModule(module, collector, writeActions)
            }
        }

        writeActions.forEach { it() }

        configureKotlinSettings(modulesToConfigure)

        collector.showNotification()
    }

    fun getOrCreateKotlinLibrary(
        project: Project,
        collector: NotificationMessageCollector
    ) {
        getKotlinLibrary(project) ?: createNewLibrary(project, collector)
    }

    fun configureSilently(project: Project) {
        val collector = NotificationMessageCollector.create(project)
        getOrCreateKotlinLibrary(project, collector)
        for (module in ModuleManager.getInstance(project).modules) {
            configureModule(module, collector)
        }
    }

    open fun configureModule(module: Module, collector: NotificationMessageCollector, writeActions: MutableList<() -> Unit>? = null) {
        configureModuleWithLibrary(module, collector, writeActions)
    }

    private fun configureModuleWithLibrary(module: Module, collector: NotificationMessageCollector, writeActions: MutableList<() -> Unit>?) {
        val project = module.project

        val library = (findAndFixBrokenKotlinLibrary(module, collector)
            ?: getKotlinLibrary(module)
            ?: getKotlinLibrary(project)
            ?: error("Kotlin Library has to be created in advance")) as LibraryEx

        val sdk = module.sdk
        library.modifiableModel.let { libraryModel ->
            configureLibraryJar(project, libraryModel, libraryJarDescriptor, collector, ProgressManager.getGlobalProgressIndicator())

            // commit will be performed later on EDT
            writeActions.addOrExecute { runWriteAction { libraryModel.commit() } }
        }

        addLibraryToModuleIfNeeded(module, library, collector, writeActions)
    }

    private fun MutableList<() -> Unit>?.addOrExecute(writeAction: () -> Unit) {
        if (this != null) {
            add(writeAction)
        } else {
            writeAction()
        }
    }

    fun configureLibraryJar(
        project: Project,
        library: LibraryEx.ModifiableModelEx,
        libraryJarDescriptor: LibraryJarDescriptor,
        collector: NotificationMessageCollector,
        progressIndicator: ProgressIndicator? = null
    ) {
        library.kind = RepositoryLibraryType.REPOSITORY_LIBRARY_KIND
        val properties = libraryJarDescriptor.repositoryLibraryProperties
        library.properties = properties
        val dependencies =
            if (progressIndicator != null) {
                JarRepositoryManager.loadDependenciesSync(project, properties, true, true, null, null, progressIndicator)
            } else {
                JarRepositoryManager.loadDependenciesModal(project, properties, true, true, null, null)
            }

        dependencies.forEach {
            library.addRoot(it.file, it.type)
        }

        collector.addMessage(KotlinProjectConfigurationBundle.message("added.0.to.library.configuration", libraryJarDescriptor.mavenArtifactId))
        return
    }

    private fun getKotlinLibrary(project: Project): Library? {
        return LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.firstOrNull { isKotlinLibrary(it, project) }
            ?: LibraryTablesRegistrar.getInstance().libraryTable.libraries.firstOrNull { isKotlinLibrary(it, project) }
    }

    private fun addLibraryToModuleIfNeeded(
        module: Module,
        library: Library,
        collector: NotificationMessageCollector,
        writeActions: MutableList<() -> Unit>?
    ) {
        ApplicationManager.getApplication().assertIsNonDispatchThread()

        val model = runReadAction { ModuleRootManager.getInstance(module).modifiableModel }
        val expectedDependencyScope = runReadAction { getDependencyScope(module) }
        val kotlinLibrary = runReadAction { getKotlinLibrary(module) }
        if (kotlinLibrary == null) {
            val entry: LibraryOrderEntry = model.addLibraryEntry(library)
            entry.isExported = false
            entry.scope = expectedDependencyScope
            collector.addMessage(KotlinProjectConfigurationBundle.message("0.library.was.added.to.module.1", library.name.toString(), module.name))
        } else {
            model.findLibraryOrderEntry(kotlinLibrary)?.let { libraryEntry ->
                val libraryDependencyScope = libraryEntry.scope
                if (expectedDependencyScope != libraryDependencyScope) {
                    libraryEntry.scope = expectedDependencyScope
                    collector.addMessage(
                        KotlinProjectConfigurationBundle.message(
                            "0.library.scope.has.changed.from.1.to.2.for.module.3",
                            kotlinLibrary.name.toString(),
                            libraryDependencyScope,
                            expectedDependencyScope,
                            module.name
                        )
                    )
                }
            }
        }

        // commit will be performed later on EDT
        writeActions.addOrExecute { runWriteAction { model.commit() } }
    }

    private fun createNewLibrary(
        project: Project,
        collector: NotificationMessageCollector
    ): Library {
        val table = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
        val library = runWriteAction {
            table.modifiableModel.run {
                val library = createLibrary(libraryName, libraryType.kind)
                commit()
                library
            }
        }

        collector.addMessage(KotlinProjectConfigurationBundle.message("0.library.was.created", library.name.toString()))
        return library
    }

    private fun getKotlinLibrary(module: Module): Library? {
        val project = module.project
        return module.findLibrary { isKotlinLibrary(it, project) }
    }

    private fun isKotlinLibrary(library: Library, project: Project): Boolean {
        return library.name == libraryName || stdlibDetector.isStdlib(project, library)
    }

    protected open fun configureKotlinSettings(modules: List<Module>) {}

    protected open fun findAndFixBrokenKotlinLibrary(module: Module, collector: NotificationMessageCollector): Library? = null

    protected open fun isApplicable(module: Module): Boolean {
        return module.buildSystemType == BuildSystemType.JPS
    }

    override fun changeGeneralFeatureConfiguration(
        module: Module,
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    ) {
        val sinceVersion = feature.sinceApiVersion

        if (state != LanguageFeature.State.DISABLED &&
            getRuntimeLibraryVersionOrDefault(module).apiVersion < sinceVersion &&
            !askUpdateRuntime(module, sinceVersion)
        ) {
            return
        }

        val facetSettings = KotlinFacetSettingsProvider.getInstance(module.project)?.getInitializedSettings(module)
        if (facetSettings != null) {
            ModuleRootModificationUtil.updateModel(module) {
                facetSettings.apiLevel = feature.sinceVersion
                facetSettings.languageLevel = feature.sinceVersion
                facetSettings.compilerSettings?.apply {
                    additionalArguments = additionalArguments.replaceLanguageFeature(
                        feature,
                        state,
                        getRuntimeLibraryVersion(module),
                        separator = " ",
                        quoted = false
                    )
                }
            }
        }
    }

    override fun updateLanguageVersion(
        module: Module,
        languageVersion: String?,
        apiVersion: String?,
        requiredStdlibVersion: ApiVersion,
        forTests: Boolean
    ) {
        val runtimeUpdateRequired = getRuntimeLibraryVersion(module)?.apiVersion?.let { runtimeVersion ->
            runtimeVersion < requiredStdlibVersion
        } ?: false

        if (runtimeUpdateRequired && !askUpdateRuntime(module, requiredStdlibVersion)) {
            return
        }

        val facetSettings = KotlinFacetSettingsProvider.getInstance(module.project)?.getSettings(module)
        if (facetSettings != null) {
            val model = FacetManager.getInstance(module).createModifiableModel()
            with(facetSettings) {
                if (languageVersion != null) {
                    languageLevel = LanguageVersion.fromVersionString(languageVersion)
                }
                if (apiVersion != null) {
                    apiLevel = LanguageVersion.fromVersionString(apiVersion)
                }
            }
            runWriteAction {
                model.commit()
            }
        }
    }

    override fun addLibraryDependency(
        module: Module,
        element: PsiElement,
        library: ExternalLibraryDescriptor,
        libraryJarDescriptor: LibraryJarDescriptor,
        scope: DependencyScope
    ) {
        val project = module.project

        var foundLibrary: Library? = null
        // TODO: in our case any PROJECT (not module) library (especially unused)
        //  would fit but I failed to find API for traversing such libraries.
        //  Current solution traverses only used project libraries
        project.forEachAllUsedLibraries {
            if (libraryJarDescriptor.findExistingJar(it) != null && it.safeAs<LibraryEx>()?.module?.equals(null) == true) {
                foundLibrary = it
                return@forEachAllUsedLibraries false
            }
            return@forEachAllUsedLibraries true
        }
        foundLibrary?.let {
            ModuleRootModificationUtil.addDependency(module, it, scope, false)
        }

        val kotlinStdlibVersion = module.findLibrary { isKotlinLibrary(it, project) }
            ?.safeAs<LibraryEx>()?.properties?.safeAs<RepositoryLibraryProperties>()?.version
        RepositoryAddLibraryAction.addLibraryToModule(
            RepositoryLibraryDescription.findDescription(libraryJarDescriptor.repositoryLibraryProperties),
            module,
            kotlinStdlibVersion ?: KotlinPluginLayout.standaloneCompilerVersion.artifactVersion,
            scope,
            /* downloadSources = */ true,
            /* downloadJavaDocs = */ true
        )
    }

    companion object {
        private fun getDependencyScope(module: Module): DependencyScope =
            if (hasKotlinFilesInTestsOnly(module)) DependencyScope.TEST else DependencyScope.COMPILE
    }
}
