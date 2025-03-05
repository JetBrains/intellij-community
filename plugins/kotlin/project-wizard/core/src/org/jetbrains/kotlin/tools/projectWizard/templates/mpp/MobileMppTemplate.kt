// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.templates.mpp

import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.TaskResult
import org.jetbrains.kotlin.tools.projectWizard.core.Writer
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.KotlinBuildSystemPluginIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.MppModuleConfigurator
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.SimpleTargetConfigurator
import org.jetbrains.kotlin.tools.projectWizard.mpp.applyMppStructure
import org.jetbrains.kotlin.tools.projectWizard.mpp.mppSources
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.plugins.pomIR
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.SourcesetType
import org.jetbrains.kotlin.tools.projectWizard.settings.javaPackage
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import org.jetbrains.kotlin.tools.projectWizard.templates.Template

class MobileMppTemplate : Template() {
    @NonNls
    override val id: String = "mobileMppModule"

    override val title: String = KotlinNewProjectWizardBundle.message("module.template.mpp.mobile.title")
    override val description: String = KotlinNewProjectWizardBundle.message("module.template.mpp.mobile.description")

    override fun isApplicableTo(module: Module, projectKind: ProjectKind, reader: Reader): Boolean =
        module.configurator == MppModuleConfigurator


    override fun Writer.getIrsToAddToBuildFile(module: ModuleIR): List<BuildSystemIR> = irsList {
        val cocoaPods = module.originalModule.subModules.any {
            (it.configurator as? SimpleTargetConfigurator)?.moduleSubType == ModuleSubType.iosCocoaPods
        }

        if (cocoaPods) {
            +KotlinBuildSystemPluginIR(KotlinBuildSystemPluginIR.Type.nativeCocoapods, null)
        }
    }

    override fun Writer.runArbitratyTask(module: ModuleIR): TaskResult<Unit> {
        val javaPackage = module.originalModule.javaPackage(pomIR())

        val mpp = mppSources(javaPackage) {
            filesFor(ModuleSubType.common) {
                file(FileTemplateDescriptor("mppCommon/Greeting.kt.vm", relativePath = null), "Greeting.kt", SourcesetType.main)
                file(FileTemplateDescriptor("mppCommon/Platform.kt.vm", relativePath = null), "Platform.kt", SourcesetType.main)
            }

            filesFor(ModuleSubType.android) {
                file(FileTemplateDescriptor("android/Platform.kt.vm", relativePath = null), "Platform.kt", SourcesetType.main)
                file(FileTemplateDescriptor("android/androidTest.kt.vm", relativePath = null), "androidTest.kt", SourcesetType.test)
            }

            filesFor(ModuleSubType.iosArm64, ModuleSubType.iosX64, ModuleSubType.ios, ModuleSubType.iosCocoaPods) {
                file(FileTemplateDescriptor("ios/Platform.kt.vm", relativePath = null), "Platform.kt", SourcesetType.main)
                file(FileTemplateDescriptor("ios/iosTest.kt.vm", relativePath = null), "iosTest.kt", SourcesetType.test)
            }

        }

        return applyMppStructure(mpp, module.originalModule, module.path)
    }
}