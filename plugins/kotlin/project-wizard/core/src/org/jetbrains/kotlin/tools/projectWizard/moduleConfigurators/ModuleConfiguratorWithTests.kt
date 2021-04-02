// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators


import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizardBundle
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.core.Reader
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSetting
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.ModuleConfiguratorSettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.core.safeAs
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.*
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.gradle.irsList
import org.jetbrains.kotlin.tools.projectWizard.ir.buildsystem.maven.MavenOnlyPluginIR
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.phases.GenerationPhase
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModuleType
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ModulesToIrConversionData
import org.jetbrains.kotlin.tools.projectWizard.settings.DisplayableSettingItem
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Module
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.ModuleKind

interface ModuleConfiguratorWithTests : ModuleConfiguratorWithSettings {
    companion object : ModuleConfiguratorSettings() {
        val testFramework by enumSetting<KotlinTestFramework>(
            KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework"),
            neededAtPhase = GenerationPhase.PROJECT_GENERATION
        ) {
            filter = filter@{ reference, kotlinTestFramework ->
                val module = getModule(reference) ?: return@filter false
                val configurator = module.configurator
                when {
                    kotlinTestFramework == KotlinTestFramework.NONE -> {
                        val parent = module.parent
                        module.kind != ModuleKind.target || (parent?.let { getTestFramework(it) } == KotlinTestFramework.NONE)
                    }
                    configurator == MppModuleConfigurator -> kotlinTestFramework == KotlinTestFramework.COMMON
                    configurator is ModuleConfiguratorWithModuleType -> configurator.moduleType in kotlinTestFramework.moduleTypes
                    else -> false
                }
            }
            defaultValue = dynamic { reference ->
                if (buildSystemType == BuildSystemType.Jps) return@dynamic KotlinTestFramework.NONE
                val module = getModule(reference) ?: return@dynamic KotlinTestFramework.NONE
                module.configurator.safeAs<ModuleConfiguratorWithTests>()?.defaultTestFramework()
            }
        }

        private fun getModule(reference: SettingReference<*, *>): Module? {
            if (reference !is ModuleConfiguratorSettingReference<*, *>) return null
            return reference.module
        }

        private fun Reader.getTestFramework(module: Module): KotlinTestFramework =
            inContextOfModuleConfigurator(module) { testFramework.reference.settingValue }
    }

    fun defaultTestFramework(): KotlinTestFramework

    override fun createModuleIRs(
        reader: Reader,
        configurationData: ModulesToIrConversionData,
        module: Module
    ): List<BuildSystemIR> = irsList {
        reader {
            if (buildSystemType.isGradle) {
                val mppModule = module.parent
                val testFramework = getTestFramework(
                    if (module.configurator is CommonTargetConfigurator) mppModule!! else module
                )

                if (testFramework != KotlinTestFramework.NONE) {
                    if (module.configurator !is TargetConfigurator
                        || module.configurator is CommonTargetConfigurator
                        || mppModule?.subModules?.none { it.configurator is CommonTargetConfigurator } == true
                    ) {
                        +createTestFramework("test", module)
                    }
                }
            } else {
                val testFramework = getTestFramework(module)
                testFramework.dependencyNames.forEach { dependencyName ->
                    +createTestFramework(dependencyName, module)
                }
                testFramework.additionalDependencies.forEach { +it }
            }
        }
    }


    override fun createBuildFileIRs(reader: Reader, configurationData: ModulesToIrConversionData, module: Module) = irsList {
        val testFramework = inContextOfModuleConfigurator(module) { reader { testFramework.reference.settingValue } }
        val buildSystemType = reader { buildSystemType }
        if (testFramework != KotlinTestFramework.NONE) {
            when {
                module.kind.isSinglePlatform && buildSystemType.isGradle -> {
                    testFramework.usePlatform?.let { usePlatform ->
                        val testTaskAccess = if (buildSystemType == BuildSystemType.GradleKotlinDsl) "tasks.test" else "test"
                        testTaskAccess {
                            +"$usePlatform()"
                        }
                    }
                }
                buildSystemType == BuildSystemType.Maven -> {
                    +MavenOnlyPluginIR("maven-surefire-plugin", Versions.MAVEN_PLUGINS.SUREFIRE)
                    +MavenOnlyPluginIR("maven-failsafe-plugin", Versions.MAVEN_PLUGINS.FAILSAFE)
                }
            }
        }
    }

    override fun getConfiguratorSettings(): List<ModuleConfiguratorSetting<*, *>> = listOf(testFramework)

    private fun Reader.createTestFramework(name: String, module: Module) = KotlinArbitraryDependencyIR(
        name = name,
        isInMppModule = module.kind
            .let { it == ModuleKind.multiplatform || it == ModuleKind.target },
        kotlinVersion = KotlinPlugin.version.propertyValue,
        dependencyType = DependencyType.TEST
    )
}

enum class KotlinTestFramework(
    override val text: String,
    val moduleTypes: List<ModuleType>,
    val usePlatform: String?,
    val dependencyNames: List<String>,
    val additionalDependencies: List<LibraryDependencyIR> = emptyList()
) : DisplayableSettingItem {
    NONE(
        KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework.none"),
        ModuleType.ALL.toList(),
        usePlatform = null,
        emptyList()
    ),
    JUNIT4(
        KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework.junit4"),
        listOf(ModuleType.jvm, ModuleType.android),
        usePlatform = "useJUnit",
        listOf("test-junit")
    ),
    JUNIT5(
        KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework.junit5"),
        listOf(ModuleType.jvm),
        usePlatform = "useJUnitPlatform",
        dependencyNames = listOf("test-junit5"),
        additionalDependencies = listOf(
            ArtifactBasedLibraryDependencyIR(
                MavenArtifact(DefaultRepository.MAVEN_CENTRAL, "org.junit.jupiter", "junit-jupiter-engine"),
                version = Versions.JUNIT5,
                dependencyType = DependencyType.TEST,
                dependencyKind = DependencyKind.runtimeOnly
            ),
        )
    ),
    TEST_NG(
        KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework.test.ng"),
        listOf(ModuleType.jvm),
        usePlatform = "useTestNG",
        listOf("test-testng")
    ),
    JS(
        KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework.kotlin.test"),
        listOf(ModuleType.js),
        usePlatform = null,
        listOf("test-js")
    ),
    COMMON(
        KotlinNewProjectWizardBundle.message("module.configurator.tests.setting.framework.kotlin.test"),
        listOf(ModuleType.common),
        usePlatform = null,
        listOf("test-common", "test-annotations-common")
    )
}

val KotlinTestFramework.isPresent
    get() = this != KotlinTestFramework.NONE
