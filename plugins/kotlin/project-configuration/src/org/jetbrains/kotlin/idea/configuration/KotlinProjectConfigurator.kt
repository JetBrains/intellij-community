// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.base.projectStructure.toModuleGroup
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.platform.TargetPlatform

enum class ConfigureKotlinStatus {
    /** Kotlin is correctly configured using this configurator. */
    CONFIGURED,

    /** The configurator is not applicable to the current project type. */
    NON_APPLICABLE,

    /** The configurator is applicable to the current project type and can configure Kotlin automatically. */
    CAN_BE_CONFIGURED,

    /**
     * The configurator is applicable to the current project type and Kotlin is not configured,
     * but the state of the project doesn't allow to configure Kotlin automatically.
     */
    BROKEN
}

class AutoConfigurationSettings(
    val module: Module,
    val kotlinVersion: IdeKotlinVersion
)

interface KotlinProjectConfigurator {

    /**
     * Returns false if the Kotlin not configured dialog should be suppressed even when Kotlin is not configured.
     */
    fun shouldShowNotConfiguredDialog(): Boolean = true

    /**
     * Checks if the [module] can be automatically configured with Kotlin.
     * Returns the settings that can be configured, or null if automatic configuration is not possible.
     *
     * Note: This function is called from a background thread in a background task.
     * Implementations are expected to make sure they obtain read-locks within this function appropriately.
     */
    suspend fun calculateAutoConfigSettings(module: Module): AutoConfigurationSettings? = null

    /**
     * Returns true if automatic configuration is available with this configurator.
     */
    fun canRunAutoConfig(): Boolean = false

    /**
     * Automatically configures the module specified in the [settings] previously calculated using [calculateAutoConfigSettings].
     *
     * Note: This function is called from a background thread.
     * Implementations are expected to make sure they obtain read/write-locks within this function appropriately.
     */
    suspend fun runAutoConfig(settings: AutoConfigurationSettings) {
        throw NotImplementedError("Auto-configuration is not implemented for this configurator")
    }

    /**
     * Returns true if the [module] could be configured by this configurator, or is already configured.
     */
    fun isApplicable(module: Module): Boolean {
        val status = getStatus(module.toModuleGroup())
        return status == ConfigureKotlinStatus.CAN_BE_CONFIGURED || status == ConfigureKotlinStatus.CONFIGURED
    }

    fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus

    /**
     * Returns a collection of configured base modules.
     * Note that the default implementation deliberately doesn't do anything but always returns an empty collection for the sake of
     * API compatibility.
     */
    @JvmSuppressWildcards
    fun configureAndGetConfiguredModules(project: Project, excludeModules: Collection<Module>): Set<Module> {
        // Stub for not breaking external API implementations
        return emptySet()
    }

    @RequiresEdt
    @JvmSuppressWildcards
    fun configure(project: Project, excludeModules: Collection<Module>)

    fun queueSyncIfNeeded(project: Project) {
        // Do nothing here. Stub for not breaking external API implementations
    }

    suspend fun queueSyncAndWaitForProjectToBeConfigured(project: Project) {
        // Do nothing here. Stub for not breaking external API implementations
    }

    val presentableText: String

    val name: String

    val targetPlatform: TargetPlatform

    /**
     * The name that the user interacts with through the build system when referring
     * to the given [module].
     *
     * For instance, in KMP we often create IntelliJ modules for each fragment
     * (`my-module.commonMain`) but users only really see `my-module` as a module.
     */
    fun userVisibleNameFor(module: Module): @NlsSafe String = module.name

    fun updateLanguageVersion(
        module: Module,
        languageVersion: String?,
        apiVersion: String?,
        requiredStdlibVersion: ApiVersion,
        forTests: Boolean
    )

    fun changeGeneralFeatureConfiguration(
        module: Module,
        feature: LanguageFeature,
        state: LanguageFeature.State,
        forTests: Boolean
    )

    @Deprecated(
        "Please implement/use the KotlinBuildSystemDependencyManager EP instead.", ReplaceWith(
            "KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)?.addDependency(module, library.withScope(scope))"
        )
    )
    fun addLibraryDependency(
        module: Module,
        element: PsiElement,
        library: ExternalLibraryDescriptor,
        libraryJarDescriptor: LibraryJarDescriptor,
        scope: DependencyScope
    ) {
        KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)?.addDependency(module, library.withScope(scope))
    }

    /**
     * Whether this configurator supports adding module-wide opt-ins via [addModuleWideOptIn].
     * If this configurator returns `true`, it must provide a valid implementation for [addModuleWideOptIn].
     */
    val canAddModuleWideOptIn: Boolean
        get() = false

    /**
     * Adds a module-wide opt-in for the given [annotationFqName] in the given [module].
     * 
     * The [compilerArgument] is a convenience for implementations that use raw compiler arguments. It already contains the correct
     * compiler argument name for the current Kotlin version (`-Xuse-experimental`, `-Xopt-in`, `-opt-in`) and the annotation name.
     */
    fun addModuleWideOptIn(module: Module, annotationFqName: FqName, compilerArgument: String) {
        throw UnsupportedOperationException("Cannot add module-wide opt-in with this configurator (${this::class.qualifiedName})")
    }

    companion object {
        val EP_NAME: ExtensionPointName<KotlinProjectConfigurator> = ExtensionPointName.create<KotlinProjectConfigurator>("org.jetbrains.kotlin.projectConfigurator")
    }
}
