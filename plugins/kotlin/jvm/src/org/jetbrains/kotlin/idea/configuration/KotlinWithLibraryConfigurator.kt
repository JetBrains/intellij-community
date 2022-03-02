// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryAddLibraryAction
import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.application.ApplicationManager
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
import org.jetbrains.kotlin.cli.common.arguments.CliArgumentStringBuilder.replaceLanguageFeature
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.KotlinFacetSettingsProvider
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.facet.getCleanRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.facet.getRuntimeLibraryVersion
import org.jetbrains.kotlin.idea.facet.toApiVersion
import org.jetbrains.kotlin.idea.framework.ui.CreateLibraryDialogWithModules
import org.jetbrains.kotlin.idea.quickfix.askUpdateRuntime
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils.underModalProgress
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils.underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.projectStructure.findLibrary
import org.jetbrains.kotlin.idea.util.projectStructure.sdk
import org.jetbrains.kotlin.idea.versions.*
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class KotlinWithLibraryConfigurator<P : LibraryProperties<*>> protected constructor() : KotlinProjectConfigurator {
    protected abstract val libraryName: String

    protected abstract val messageForOverrideDialog: String

    protected abstract val dialogTitle: String

    abstract val libraryType: LibraryType<P>

    abstract val libraryJarDescriptor: LibraryJarDescriptor

    abstract val libraryProperties: P

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
            underModalProgressOrUnderWriteActionWithNonCancellableProgressInDispatchThread(project, KotlinJvmBundle.message("lookup.modules.configurations.progress.text")) {
                getCanBeConfiguredModules(project, this)
            }
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


        val collector = createConfigureKotlinNotificationCollector(project)
        getOrCreateKotlinLibrary(project, collector)
        val writeActions = mutableListOf<() -> Unit>()
        underModalProgress(
            project,
            KotlinJvmBundle.message("configure.kotlin.in.modules.progress.text")) {
            val progressIndicator = ProgressManager.getGlobalProgressIndicator()
            for ((index, module) in modulesToConfigure.withIndex()) {
                if (!isUnitTestMode()) {
                    progressIndicator?.let {
                        it.checkCanceled()
                        it.fraction = index * 1.0 / modulesToConfigure.size
                        it.text = KotlinJvmBundle.message("configure.kotlin.in.modules.progress.text")
                        it.text2 = KotlinJvmBundle.message("configure.kotlin.in.module.0.progress.text", module.name)
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

    @Suppress("unused") // Please do not delete this function (used in ProcessingKt plugin)
    fun configureSilently(project: Project) {
        val collector = createConfigureKotlinNotificationCollector(project)
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

        collector.addMessage(KotlinJvmBundle.message("added.0.to.library.configuration", libraryJarDescriptor.mavenArtifactId))
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
            collector.addMessage(KotlinJvmBundle.message("0.library.was.added.to.module.1", library.name.toString(), module.name))
        } else {
            model.findLibraryOrderEntry(kotlinLibrary)?.let { libraryEntry ->
                val libraryDependencyScope = libraryEntry.scope
                if (expectedDependencyScope != libraryDependencyScope) {
                    libraryEntry.scope = expectedDependencyScope
                    collector.addMessage(
                        KotlinJvmBundle.message(
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

    fun createNewLibrary(
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

        collector.addMessage(KotlinJvmBundle.message("0.library.was.created", library.name.toString()))
        return library
    }

    protected abstract val libraryMatcher: (Library, Project) -> Boolean

    fun getKotlinLibrary(module: Module): Library? = findKotlinRuntimeLibrary(module, this::isKotlinLibrary)

    private fun isKotlinLibrary(library: Library, project: Project) = library.name == libraryName || libraryMatcher(library, project)

    protected open fun configureKotlinSettings(modules: List<Module>) {
    }

    protected open fun findAndFixBrokenKotlinLibrary(module: Module, collector: NotificationMessageCollector): Library? = null

    protected open fun isApplicable(module: Module): Boolean {
        return module.getBuildSystemType() == BuildSystemType.JPS
    }

    override fun changeGeneralFeatureConfiguration(
        module: Module,
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    ) {
        val sinceVersion = feature.sinceApiVersion

        if (state != LanguageFeature.State.DISABLED &&
            getRuntimeLibraryVersion(module).toApiVersion() < sinceVersion &&
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
                        getCleanRuntimeLibraryVersion(module),
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
        val runtimeUpdateRequired = getRuntimeLibraryVersion(module)?.let { ApiVersion.parse(it) }?.let { runtimeVersion ->
            runtimeVersion < requiredStdlibVersion
        } ?: false

        if (runtimeUpdateRequired && !askUpdateRuntime(module, requiredStdlibVersion)) {
            return
        }

        val facetSettings = KotlinFacetSettingsProvider.getInstance(module.project)?.getInitializedSettings(module)
        if (facetSettings != null) {
            ModuleRootModificationUtil.updateModel(module) {
                with(facetSettings) {
                    if (languageVersion != null) {
                        languageLevel = LanguageVersion.fromVersionString(languageVersion)
                    }
                    if (apiVersion != null) {
                        apiLevel = LanguageVersion.fromVersionString(apiVersion)
                    }
                }
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

        // TODO: in our case any PROJECT (not module) library (especially unused)
        //  would fit but I failed to find API for traversing such libraries.
        //  Current solution traverses only used project libraries
        findAllUsedLibraries(project).keySet()
            .firstOrNull { libraryJarDescriptor.findExistingJar(it) != null && it.safeAs<LibraryEx>()?.module?.equals(null) == true }
            ?.let {
                ModuleRootModificationUtil.addDependency(module, it, scope, false)
                return
            }

        val kotlinStdlibVersion = module.findLibrary { isKotlinLibrary(it, project) }
            ?.safeAs<LibraryEx>()?.properties?.safeAs<RepositoryLibraryProperties>()?.version
        RepositoryAddLibraryAction.addLibraryToModule(
            RepositoryLibraryDescription.findDescription(libraryJarDescriptor.repositoryLibraryProperties),
            module,
            kotlinStdlibVersion ?: KotlinPluginLayout.instance.lastStableKnownCompilerVersionShort,
            scope,
            /* downloadSources = */ true,
            /* downloadJavaDocs = */ true
        )
    }

    companion object {
        private fun getDependencyScope(module: Module): DependencyScope =
            if (hasKotlinFilesOnlyInTests(module)) DependencyScope.TEST else DependencyScope.COMPILE
    }
}
