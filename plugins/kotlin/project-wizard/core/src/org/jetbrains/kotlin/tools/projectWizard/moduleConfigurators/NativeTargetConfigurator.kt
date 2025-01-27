// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators

import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.BuildSystemIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.DefaultTargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.multiplatform.NonDefaultTargetConfigurationIR
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleSubType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.isIOS
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.isNativeDesktop
import org.jetbrains.kotlin.tools.projectWizard.plugins.printer.GradlePrinter
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import java.util.*

interface NativeTargetConfigurator : TargetConfigurator {
    val isDesktopTarget: Boolean
    val isIosTarget: Boolean
}

class RealNativeTargetConfigurator private constructor(
    override val moduleSubType: ModuleSubType
) : NativeTargetConfigurator, SimpleTargetConfigurator {
    override val text: String = moduleSubType.name.capitalize(Locale.US)
    override val isDesktopTarget: Boolean
        get() = moduleSubType.isNativeDesktop

    override val isIosTarget: Boolean
        get() = moduleSubType.isIOS

    override fun createInnerTargetIrs(reader: Reader, module: Module): List<BuildSystemIR> = irsList {
        +super<SimpleTargetConfigurator>.createInnerTargetIrs(reader, module)

        val dependsOnCocoapodsModule: Boolean = module.sourceSets.any { sourceSet ->
            sourceSet.dependsOnModules.any { module ->
                module.configurator == configuratorsByModuleType.getValue(ModuleSubType.iosCocoaPods)
            }
        }
        if (moduleSubType.isIOS && moduleSubType != ModuleSubType.iosCocoaPods && !dependsOnCocoapodsModule) {
            "binaries" {
                "framework"  {
                    "baseName" assign const(module.parent!!.name)
                }
            }
        }
    }

    override fun Reader.createTargetIrs(module: Module): List<BuildSystemIR> = irsList {
        +DefaultTargetConfigurationIR(
            module.createTargetAccessIr(moduleSubType),
            createInnerTargetIrs(this@createTargetIrs, module).toPersistentList()
        )
        if (moduleSubType == ModuleSubType.iosCocoaPods) {
            "cocoapods" {
                "summary" assign const("Some description for the Shared Module")
                "homepage" assign const("Link to the Shared Module homepage")
                "ios.deploymentTarget" assign const("14.1")
                "podfile" assign raw("project.file(\"../iosApp/Podfile\")") //TODO hardcoded name
                "framework"  {
                    "baseName" assign const(module.parent!!.name)
                }
            }
        }
    }


    companion object {
        val configurators = ModuleSubType.values()
            .filter { it.moduleType == ModuleType.native }
            .map(::RealNativeTargetConfigurator)

        val configuratorsByModuleType = configurators.associateBy { it.moduleSubType }
    }
}

object NativeForCurrentSystemTarget : NativeTargetConfigurator, SingleCoexistenceTargetConfigurator {
    override val moduleType = ModuleType.native
    override val isDesktopTarget: Boolean = true
    override val isIosTarget: Boolean = false

    @NonNls
    override val id = "nativeForCurrentSystem"

    override val text = KotlinNewProjectWizardBundle.message("module.configurator.native.for.current.system")


    override fun Reader.createTargetIrs(
        module: Module
    ): List<BuildSystemIR> {
        val moduleName = module.name
        val variableName = "${moduleName}Target"

        return irsList {
            "hostOs" createValue raw("System.getProperty(\"os.name\")")
            "isArm64" createValue raw("System.getProperty(\"os.arch\") == \"aarch64\"")
            "isMingwX64" createValue raw("hostOs.startsWith(\"Windows\")")

            addRaw {
                when (dsl) {
                    GradlePrinter.GradleDsl.KOTLIN -> {
                        +"val $variableName = when "
                        inBrackets {
                            indent()
                            +"""hostOs == "Mac OS X" && isArm64 -> macosArm64("$moduleName")"""; nlIndented()
                            +"""hostOs == "Mac OS X" && !isArm64 -> macosX64("$moduleName")"""; nlIndented()
                            +"""hostOs == "Linux" && isArm64 -> linuxArm64("$moduleName")"""; nlIndented()
                            +"""hostOs == "Linux" && !isArm64 -> linuxX64("$moduleName")"""; nlIndented()
                            +"""isMingwX64 -> mingwX64("$moduleName")"""; nlIndented()
                            +"""else -> throw GradleException("Host OS is not supported in Kotlin/Native.")"""
                        }
                    }
                    GradlePrinter.GradleDsl.GROOVY -> {
                        +"""def $variableName"""; nlIndented()
                        +"""if (hostOs == "Mac OS X" && isArm64) $variableName = macosArm64('$moduleName')"""; nlIndented()
                        +"""else if (hostOs == "Mac OS X" && !isArm64) $variableName = macosX64('$moduleName')"""; nlIndented()
                        +"""else if (hostOs == "Linux" && isArm64) $variableName = linuxArm64("$moduleName")"""; nlIndented()
                        +"""else if (hostOs == "Linux" && !isArm64) $variableName = linuxX64("$moduleName")"""; nlIndented()
                        +"""else if (isMingwX64) $variableName = mingwX64("$moduleName")"""; nlIndented()
                        +"""else throw new GradleException("Host OS is not supported in Kotlin/Native.")"""
                    }
                }
                nl()
            }

            +NonDefaultTargetConfigurationIR(
                variableName = variableName,
                targetName = moduleName,
                irs = createInnerTargetIrs(this@createTargetIrs, module).toPersistentList()
            )
        }
    }
}
