// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.jarRepository.RepositoryLibraryType
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.StdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.getOrCreateFacet
import org.jetbrains.kotlin.idea.facet.initializeIfNeeded
import org.jetbrains.kotlin.idea.projectConfiguration.JavaRuntimeLibraryDescription
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.projectConfiguration.LibraryJarDescriptor
import org.jetbrains.kotlin.idea.serialization.updateCompilerArguments
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

open class KotlinJavaModuleConfigurator : KotlinWithLibraryConfigurator<RepositoryLibraryProperties>() {
    override fun isApplicable(module: Module): Boolean {
        return super.isApplicable(module) && !hasBrokenJsRuntime(module)
    }

    override val libraryType: RepositoryLibraryType get() = RepositoryLibraryType.getInstance()

    override val libraryProperties: RepositoryLibraryProperties get() = libraryJarDescriptor.repositoryLibraryProperties

    override val stdlibDetector: StdlibDetectorFacility
        get() = KotlinJvmStdlibDetectorFacility

    override fun isConfigured(module: Module): Boolean {
        return hasKotlinJvmRuntimeInScope(module)
    }

    override val libraryName: String
        get() = JavaRuntimeLibraryDescription.LIBRARY_NAME

    override val dialogTitle: String
        get() = JavaRuntimeLibraryDescription.DIALOG_TITLE

    override val messageForOverrideDialog: String
        @Nls
        get() = JavaRuntimeLibraryDescription.JAVA_RUNTIME_LIBRARY_CREATION

    override val presentableText: String
        get() = KotlinProjectConfigurationBundle.message("language.name.java")

    override val name: String
        get() = NAME

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override val libraryJarDescriptor: LibraryJarDescriptor get() = LibraryJarDescriptor.RUNTIME_JDK8_JAR

    override fun configureKotlinSettings(modules: List<Module>) {
        val project = modules.firstOrNull()?.project ?: return
        val canChangeProjectSettings = project.modules
            .asSequence()
            .mapNotNull { it.sdk }
            .mapNotNull { JavaSdk.getInstance().getVersion(it) }
            .all { it.isAtLeast(JavaSdkVersion.JDK_1_8) }

        if (canChangeProjectSettings) {
            Kotlin2JvmCompilerArgumentsHolder.getInstance(project).update {
                jvmTarget = "1.8"
            }
        } else {
            for (module in modules) {
                val sdk = module.sdk ?: continue
                val sdkVersion = JavaSdk.getInstance().getVersion(sdk) ?: continue

                if (sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_8)) {
                    val modelsProvider = ProjectDataManager.getInstance().createModifiableModelsProvider(project)
                    try {
                        val facet = module.getOrCreateFacet(modelsProvider, useProjectSettings = false, commitModel = true)
                        val facetSettings = facet.configuration.settings
                        facetSettings.initializeIfNeeded(module, null, JvmPlatforms.jvm8)
                        facetSettings.updateCompilerArguments {
                            (this as? K2JVMCompilerArguments)?.jvmTarget = "1.8"
                        }
                    } finally {
                        modelsProvider.dispose()
                    }
                }
            }
        }
    }

    override fun configureModule(module: Module, collector: NotificationMessageCollector, writeActions: MutableList<() -> Unit>?) {
        configureModuleAndGetResult(module, collector, writeActions)
    }

    override fun configureModuleAndGetResult(
        module: Module,
        collector: NotificationMessageCollector,
        writeActions: MutableList<() -> Unit>?
    ): Boolean {
        val configured = super.configureModuleAndGetResult(module, collector, writeActions)

        val callable = addStdlibToJavaModuleInfoLazy(module, collector) ?: return configured

        if (writeActions != null) {
            writeActions.add(callable)
        } else {
            callable()
        }
        return configured
    }

    companion object {
        const val NAME: String = "java"

        val instance: KotlinJavaModuleConfigurator
            get() {
                @Suppress("DEPRECATION")
                return Extensions.findExtension(KotlinProjectConfigurator.EP_NAME, KotlinJavaModuleConfigurator::class.java)
            }
    }

    private fun hasBrokenJsRuntime(module: Module): Boolean {
        for (orderEntry in ModuleRootManager.getInstance(module).orderEntries) {
            val library = (orderEntry as? LibraryOrderEntry)?.library as? LibraryEx ?: continue
            if (KotlinJavaScriptStdlibDetectorFacility.isStdlib(module.project, library, ignoreKind = true)) return true
        }
        return false
    }
}
