// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootGroup
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
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

interface KotlinProjectConfigurator {

    fun getStatus(moduleSourceRootGroup: ModuleSourceRootGroup): ConfigureKotlinStatus

    @JvmSuppressWildcards
    fun configure(project: Project, excludeModules: Collection<Module>)

    val presentableText: String

    val name: String

    val targetPlatform: TargetPlatform

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

    fun addLibraryDependency(
        module: Module,
        element: PsiElement,
        library: ExternalLibraryDescriptor,
        libraryJarDescriptor: LibraryJarDescriptor,
        scope: DependencyScope
    )

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinProjectConfigurator>("org.jetbrains.kotlin.projectConfigurator")
    }
}
