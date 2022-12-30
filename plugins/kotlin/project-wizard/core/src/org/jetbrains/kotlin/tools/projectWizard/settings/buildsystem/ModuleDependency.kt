// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem

import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.DependencyType
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.GradleRootProjectDependencyIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.ModuleDependencyIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleBinaryExpressionIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleByClassTasksCreateIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.GradleConfigureTaskIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.isGradle
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.plugins.projectPath
import org.jetbrains.kotlin.tools.projectWizard.plugins.templates.TemplatesPlugin
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplate
import org.jetbrains.kotlin.tools.projectWizard.templates.FileTemplateDescriptor
import java.nio.file.Path
import kotlin.reflect.KClass

sealed class ModuleDependencyType(
    val from: KClass<out ModuleConfigurator>,
    val to: KClass<out ModuleConfigurator>
) {
    fun accepts(from: Module, to: Module) =
        this.from.isInstance(from.configurator)
                && this.to.isInstance(to.configurator)
                && additionalAcceptanceChecker(from, to)

    open fun additionalAcceptanceChecker(from: Module, to: Module) = true

    open fun createDependencyIrs(from: Module, to: Module, data: ModulesToIrConversionData): List<BuildSystemIR> {
        val path = to.path
        val modulePomIr = data.pomIr.copy(artifactId = to.name)
        return when {
            data.isSingleRootModuleMode
                    && to.path.parts.singleOrNull() == data.rootModules.single().name
                    && data.buildSystemType.isGradle -> GradleRootProjectDependencyIR(DependencyType.MAIN)
            else -> ModuleDependencyIR(
                path.considerSingleRootModuleMode(data.isSingleRootModuleMode),
                modulePomIr,
                DependencyType.MAIN
            )
        }.asSingletonList()
    }

    open fun runArbitraryTask(
        writer: Writer,
        from: Module,
        to: Module,
        toModulePath: Path,
        data: ModulesToIrConversionData
    ): TaskResult<Unit> =
        UNIT_SUCCESS

    open fun Writer.runArbitraryTaskBeforeIRsCreated(
        from: Module,
        to: Module,
    ): TaskResult<Unit> =
        UNIT_SUCCESS

    open fun Reader.createToIRs(from: Module, to: Module, data: ModulesToIrConversionData): TaskResult<List<BuildSystemIR>> =
        Success(emptyList())

    object JVMSinglePlatformToJVMSinglePlatform : ModuleDependencyType(
        from = JvmSinglePlatformModuleConfigurator::class,
        to = JvmSinglePlatformModuleConfigurator::class
    )

    object AndroidSinglePlatformToMPP : ModuleDependencyType(
        from = AndroidSinglePlatformModuleConfigurator::class,
        to = MppModuleConfigurator::class
    )

    object AndroidTargetToMPP : ModuleDependencyType(
        from = AndroidTargetConfiguratorBase::class,
        to = MppModuleConfigurator::class
    )

    object JVMSinglePlatformToMPP : ModuleDependencyType(
        from = JvmSinglePlatformModuleConfigurator::class,
        to = MppModuleConfigurator::class
    )

    object JVMTargetToMPP : ModuleDependencyType(
        from = JvmTargetConfigurator::class,
        to = MppModuleConfigurator::class
    ) {
        override fun additionalAcceptanceChecker(from: Module, to: Module): Boolean =
            from !in to.subModules
    }

    abstract class IOSSinglePlatformToMPPBase(
        from: KClass<out IOSSinglePlatformModuleConfiguratorBase>
    ) : ModuleDependencyType(
        from,
        MppModuleConfigurator::class
    ) {
        private fun Writer.updateReference(from: Module, to: Module) = inContextOfModuleConfigurator(from) {
            IOSSinglePlatformModuleConfigurator.dependentModule.reference.update {
                IOSSinglePlatformModuleConfiguratorBase.DependentModuleReference(to).asSuccess()
            }
        }

        override fun runArbitraryTask(
            writer: Writer,
            from: Module,
            to: Module,
            toModulePath: Path,
            data: ModulesToIrConversionData
        ): TaskResult<Unit> =
            writer.updateReference(from, to)

        override fun additionalAcceptanceChecker(from: Module, to: Module): Boolean =
            to.iosTargetSafe() != null

        protected fun Module.iosTargetSafe(): Module? = subModules.firstOrNull { module ->
            module.configurator.safeAs<NativeTargetConfigurator>()?.isIosTarget == true
        }
    }

    object IOSSinglePlatformToMPP : IOSSinglePlatformToMPPBase(IOSSinglePlatformModuleConfiguratorBase::class)

    object IOSWithXcodeSinglePlatformToMPP : IOSSinglePlatformToMPPBase(
        from = IOSSinglePlatformModuleConfigurator::class,
    ) {
        override fun createDependencyIrs(from: Module, to: Module, data: ModulesToIrConversionData): List<BuildSystemIR> =
            emptyList()

        override fun runArbitraryTask(
            writer: Writer,
            from: Module,
            to: Module,
            toModulePath: Path,
            data: ModulesToIrConversionData
        ): TaskResult<Unit> = compute {
            super.runArbitraryTask(writer, from, to, toModulePath, data).ensure()
            writer.addDummyFileIfNeeded(to, toModulePath)
        }

        private fun Writer.addDummyFileIfNeeded(
            to: Module,
            toModulePath: Path,
        ): TaskResult<Unit> {
            val needDummyFile = false/*TODO*/
            return if (needDummyFile) {
                val dummyFilePath =
                    Defaults.SRC_DIR / "${to.iosTargetSafe()!!.name}Main" / to.configurator.kotlinDirectoryName / "dummyFile.kt"
                TemplatesPlugin.addFileTemplate.execute(
                    FileTemplate(
                        FileTemplateDescriptor("ios/dummyFile.kt", dummyFilePath),
                        projectPath / toModulePath
                    )
                )
            } else UNIT_SUCCESS
        }

        @OptIn(ExperimentalStdlibApi::class)
        override fun Reader.createToIRs(from: Module, to: Module, data: ModulesToIrConversionData): TaskResult<List<BuildSystemIR>> {
            val iosTargetName = to.iosTargetSafe()?.name
                ?: return Failure(
                    InvalidModuleDependencyError(
                        from, to,
                        KotlinNewProjectWizardBundle.message("error.text.module.0.should.contain.at.least.one.ios.target", to.name)
                    )
                )

            return irsList {
                +GradleConfigureTaskIR(GradleByClassTasksCreateIR("packForXcode", "Sync")) {
                    "group" assign const("build")
                    "mode" createValue GradleBinaryExpressionIR(
                        raw { +"System.getenv("; +"CONFIGURATION".quotified; +")" },
                        "?:",
                        const("DEBUG")
                    )
                    "sdkName" createValue GradleBinaryExpressionIR(
                        raw { +"System.getenv("; +"SDK_NAME".quotified; +")" },
                        "?:",
                        const("iphonesimulator")
                    )
                    "targetName" createValue raw {
                        +iosTargetName.quotified
                        when (dsl) {
                            GradlePrinter.GradleDsl.KOTLIN -> +""" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64""""
                            GradlePrinter.GradleDsl.GROOVY -> +""" + (sdkName.startsWith('iphoneos') ? 'Arm64' : 'X64')"""
                        }
                    }
                    "framework" createValue raw {
                        +"kotlin.targets"
                        when (dsl) {
                            GradlePrinter.GradleDsl.KOTLIN -> +""".getByName<KotlinNativeTarget>(targetName)"""
                            GradlePrinter.GradleDsl.GROOVY -> +"""[targetName]"""
                        }
                        +".binaries.getFramework(mode)"
                    }

                    addRaw { +"inputs.property(${"mode".quotified}, mode)" }
                    addRaw("dependsOn(framework.linkTask)")
                    "targetDir" createValue new("File", raw("buildDir"), const("xcode-frameworks"))
                    addRaw("from({ framework.outputDirectory })")
                    addRaw("into(targetDir)")
                }
                addRaw { +"""tasks.getByName(${"build".quotified}).dependsOn(packForXcode)""" }
                import("org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget")

            }.asSuccess()
        }
    }

    companion object {
        private val ALL = listOf(
            JVMSinglePlatformToJVMSinglePlatform,
            JVMSinglePlatformToMPP,
            AndroidSinglePlatformToMPP,
            AndroidTargetToMPP,
            JVMTargetToMPP,
            IOSWithXcodeSinglePlatformToMPP,
            IOSSinglePlatformToMPP,
        )

        fun getPossibleDependencyType(from: Module, to: Module): ModuleDependencyType? =
            ALL.firstOrNull { it.accepts(from, to) }

        fun isDependencyPossible(from: Module, to: Module): Boolean =
            getPossibleDependencyType(from, to) != null
    }
}