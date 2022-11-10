// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates.compose

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleImportIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.mpp.applyMppStructure
import org.jetbrains.kotlin.tools.projectWizard.mpp.mppSources
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.pomIR
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repositories
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import org.jetbrains.kotlin.tools.projectWizard.templates.Template

class ComposeMppModuleTemplate : Template() {
    @NonNls
    override val id: String = "composeMppModule"

    override val title: String = KotlinNewProjectWizardBundle.message("module.template.compose.mpp.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.compose.mpp.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator == MppModuleConfigurator && projectKind == ProjectKind.COMPOSE

    override fun Writer.getIrsToAddToBuildFile(module: ModuleIR): List<BuildSystemIR> = irsList {
        +GradleImportIR("org.jetbrains.compose.compose")
        +GradleOnlyPluginByNameIR("org.jetbrains.compose", version = Versions.JETBRAINS_COMPOSE)
        +RepositoryIR(Repositories.JETBRAINS_COMPOSE_DEV)
        +RepositoryIR(DefaultRepository.GOOGLE)
    }

    override fun Reader.updateModuleIR(module: ModuleIR): ModuleIR = when (module.originalModule.configurator) {
        CommonTargetConfigurator -> module.withIrs(
            CustomGradleDependencyDependencyIR("compose.runtime", DependencyType.MAIN, DependencyKind.api),
            CustomGradleDependencyDependencyIR("compose.foundation", DependencyType.MAIN, DependencyKind.api),
            CustomGradleDependencyDependencyIR("compose.material", DependencyType.MAIN, DependencyKind.api),
        )
        AndroidTargetConfigurator -> module.withIrs(
            DEPENDENCIES.APP_COMPAT_FOR_COMPOSE_OLD.withDependencyKind(DependencyKind.api),
            DEPENDENCIES.ANDROID_KTX_FOR_COMPOSE_OLD.withDependencyKind(DependencyKind.api)
        ).withoutIrs { it == AndroidModuleConfigurator.DEPENDENCIES.MATERIAL }
        JvmTargetConfigurator -> module.withIrs(
            CustomGradleDependencyDependencyIR("compose.preview", DependencyType.MAIN, DependencyKind.api),
        )
        else -> module
    }

    override fun Writer.runArbitratyTask(module: ModuleIR): TaskResult<Unit> {
        val webSubmodule = module.originalModule.subModules.filter({ it.template is ComposeWebModuleTemplate }).firstOrNull()
        if (webSubmodule != null) return UNIT_SUCCESS //disabling platform.kt creation for web module

        return inContextOfModuleConfigurator(module.originalModule) {
            val javaPackage = module.originalModule.javaPackage(pomIR())
            val mpp = mppSources(javaPackage) {
                mppFile("platform.kt") {
                    function("getPlatformName(): String") {
                        actualFor(ModuleSubType.jvm, actualBody = """return "Desktop" """)
                        actualFor(ModuleSubType.android, actualBody = """return "Android" """)
                        default("""return "Platform" """)
                    }
                }
                filesFor(ModuleSubType.common) {
                    file(FileTemplateDescriptor("composeMpp/App.kt.vm", relativePath = null), "App.kt", SourcesetType.main)
                }
                filesFor(ModuleSubType.jvm) {
                    file(FileTemplateDescriptor("composeMpp/DesktopApp.kt.vm", relativePath = null), "DesktopApp.kt", SourcesetType.main)
                }
            }
            applyMppStructure(mpp, module.originalModule, module.path)
        }
    }

    object DEPENDENCIES {
        @Suppress("unused")
        val ANDROID_KTX = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.core", "core-ktx"),
            version = Versions.ANDROID.ANDROIDX_KTX,
            dependencyType = DependencyType.MAIN
        )
        val ANDROID_KTX_FOR_COMPOSE_OLD = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.core", "core-ktx"),
            version = Versions.ANDROIDX_KTX_VERSION_FOR_COMPOSE_OLD,
            dependencyType = DependencyType.MAIN
        )
        val APP_COMPAT_FOR_COMPOSE_OLD = ArtifactBasedLibraryDependencyIR(
            MavenArtifact(DefaultRepository.GOOGLE, "androidx.appcompat", "appcompat"),
            version = Versions.ANDROIDX_APPCOMPAT_VERSION_FOR_COMPOSE_OLD,
            dependencyType = DependencyType.MAIN
        )
    }
}