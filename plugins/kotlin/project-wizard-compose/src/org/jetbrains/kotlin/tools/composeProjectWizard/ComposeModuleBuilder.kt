// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.gradle.GradleResourcesProvider
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.*
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlin.idea.KotlinIcons
import javax.swing.Icon

class ComposeModuleBuilder : StarterModuleBuilder() {
    companion object {
        val COMPOSE_CONFIG_TYPE_KEY: Key<ComposePWInitialStep.ComposeConfigurationType> = Key.create("compose.config.type")
        val COMPOSE_PLATFORM_KEY: Key<ComposePWInitialStep.ComposePlatform> = Key.create("compose.platform")
    }


    override fun getBuilderId(): String = "ComposeModuleBuilder"
    override fun getPresentableName(): String = ComposeProjectWizardBundle.message("module.presentation.name")
    override fun getWeight(): Int = IJ_PLUGIN_WEIGHT
    override fun getNodeIcon(): Icon = KotlinIcons.Wizard.COMPOSE
    override fun getDescription(): String = ComposeProjectWizardBundle.message("module.description")

    override fun getProjectTypes(): List<StarterProjectType> = emptyList()
    override fun getTestFrameworks(): List<StarterTestRunner> = emptyList()
    override fun getMinJavaVersion(): JavaVersion = LanguageLevel.JDK_11.toJavaVersion()

    override fun isAvailable(): Boolean {
        if (!Registry.`is`("compose.wizard.enabled", true)) {
            return false;
        }
        return super.isAvailable()
    }

    override fun getStarterPack(): StarterPack {
        return StarterPack("compose", listOf(
            Starter("compose", "Compose", getDependencyConfig("/starters/compose.pom"), emptyList())
        ))
    }

    override fun getLanguages(): List<StarterLanguage> {
        return listOf(
            KOTLIN_STARTER_LANGUAGE
        )
    }

    override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> {
        return emptyArray()
    }

    override fun createOptionsStep(contextProvider: StarterContextProvider): StarterInitialStep {
        return ComposePWInitialStep(contextProvider)
    }

    override fun getTemplateProperties(): Map<String, Any> {
        val platform = starterContext.getUserData(COMPOSE_PLATFORM_KEY)
        val configType = starterContext.getUserData(COMPOSE_CONFIG_TYPE_KEY)
        if (configType == ComposePWInitialStep.ComposeConfigurationType.MULTI_PLATFORM) {
            return mapOf("Platform" to "", "ConfigType" to configType)
        } else if (configType == ComposePWInitialStep.ComposeConfigurationType.SINGLE_PLATFORM && platform != null) {
            return mapOf("Platform" to platform, "ConfigType" to configType)
        }
        return emptyMap()
    }

    override fun getAssets(starter: Starter): List<GeneratorAsset> {
        val ftManager = FileTemplateManager.getInstance(ProjectManager.getInstance().defaultProject)

        val configType = starterContext.getUserData(COMPOSE_CONFIG_TYPE_KEY)
        val platform = starterContext.getUserData(COMPOSE_PLATFORM_KEY)
        val packagePath = starterContext.group.replace('.', '/')

        val assets = mutableListOf<GeneratorAsset>()

        assets.add(
            GeneratorTemplateFile(
                "gradle/wrapper/gradle-wrapper.properties",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_GRADLE_WRAPPER_PROPERTIES)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "gradle.properties",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_GRADLE_PROPERTIES)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "settings.gradle.kts",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_SETTINGS_GRADLE)
            )
        )
        assets.addAll(GradleResourcesProvider().getGradlewResources())



        if (configType == ComposePWInitialStep.ComposeConfigurationType.SINGLE_PLATFORM) {
            if (platform == ComposePWInitialStep.ComposePlatform.DESKTOP) {
                assets.add(
                    GeneratorTemplateFile(
                        "build.gradle.kts",
                        ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_BUILD_GRADLE)
                    )
                )
                assets.add(
                    GeneratorTemplateFile(
                        "src/jvmMain/kotlin/Main.kt",
                        ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_MAINKT)
                    )
                )
                assets.add(GeneratorEmptyDirectory("src/jvmMain/resources"))
                assets.add(GeneratorEmptyDirectory("src/jvmTest/kotlin"))
                assets.add(GeneratorEmptyDirectory("src/jvmTest/resources"))
            } else if (platform == ComposePWInitialStep.ComposePlatform.WEB) {
                assets.add(
                    GeneratorTemplateFile(
                        "build.gradle.kts",
                        ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_WEB_BUILD_GRADLE)
                    )
                )
                assets.add(
                    GeneratorTemplateFile(
                        "src/jsMain/kotlin/Main.kt",
                        ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_WEB_MAINKT)
                    )
                )
                assets.add(
                    GeneratorTemplateFile(
                        "src/jsMain/resources/index.html",
                        ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_WEB_INDEX_HTML)
                    )
                )
                assets.add(GeneratorEmptyDirectory("src/jsTest/kotlin"))
                assets.add(GeneratorEmptyDirectory("src/jsTest/resources"))
            } else {
                throw IllegalStateException("Unsupported platform!")
            }
        } else if (configType == ComposePWInitialStep.ComposeConfigurationType.MULTI_PLATFORM) {
            assets.addAll(getMppAssets(starter, ftManager, packagePath))
        }
        return assets
    }

    fun getMppAssets(starter : Starter, ftManager : FileTemplateManager, packagePath : String ): List<GeneratorAsset> {
        val assets = mutableListOf<GeneratorAsset>()

        //root
        assets.add(
            GeneratorTemplateFile(
                "build.gradle.kts",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_MPP_BUILD_GRADLE)
            )
        )

        //android
        assets.add(
            GeneratorTemplateFile(
                "android/build.gradle.kts",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_ANDROID_BUILD_GRADLE)
            )
        )
        assets.add(GeneratorEmptyDirectory("android/src/test/java"))
        assets.add(GeneratorEmptyDirectory("android/src/test/res"))
        assets.add(GeneratorEmptyDirectory("android/src/main/res"))
        assets.add(
            GeneratorTemplateFile(
                "android/src/main/AndroidManifest.xml",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_ANDROID_MANIFEST)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "android/src/main/java/${packagePath}/android/MainActivity.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_ANDROID_MAINACTIVITYKT)
            )
        )

        //common
        assets.add(
            GeneratorTemplateFile(
                "common/build.gradle.kts",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_BUILD_GRADLE)
            )
        )

        //common.android
        assets.add(
            GeneratorTemplateFile(
                "common/src/androidMain/kotlin/${packagePath}/common/platform.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_ANDROID_PLATFORMKT)
            )
        )
        assets.add(GeneratorEmptyDirectory("common/src/androidMain/resources"))
        assets.add(
            GeneratorTemplateFile(
                "common/src/androidMain/AndroidManifest.xml",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_ANDROID_MANIFEST)
            )
        )
        assets.add(GeneratorEmptyDirectory("common/src/androidTest/kotlin"))
        assets.add(GeneratorEmptyDirectory("common/src/androidTest/resources"))

        //common.common
        assets.add(
            GeneratorTemplateFile(
                "common/src/commonMain/kotlin/${packagePath}/common/platform.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_COMMON_PLATFORMKT)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "common/src/commonMain/kotlin/${packagePath}/common/App.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_COMMON_APPKT)
            )
        )
        assets.add(GeneratorEmptyDirectory("common/src/commonTest/kotlin"))
        assets.add(GeneratorEmptyDirectory("common/src/commonTest/resources"))

        //common.desktop
        assets.add(
            GeneratorTemplateFile(
                "common/src/desktopMain/kotlin/${packagePath}/common/platform.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_DESKTOP_PLATFORMKT)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "common/src/desktopMain/kotlin/${packagePath}/common/DesktopApp.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_COMMON_DESKTOP_APPKT)
            )
        )
        assets.add(GeneratorEmptyDirectory("common/src/desktopTest/kotlin"))
        assets.add(GeneratorEmptyDirectory("common/src/desktopTest/resources"))

        //desktop
        assets.add(
            GeneratorTemplateFile(
                "desktop/build.gradle.kts",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_BUILD_GRADLE)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "desktop/src/jvmMain/kotlin/Main.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_MAINKT)
            )
        )
        assets.add(GeneratorEmptyDirectory("desktop/src/jvmMain/resources"))
        assets.add(GeneratorEmptyDirectory("desktop/src/jvmTest/kotlin"))
        assets.add(GeneratorEmptyDirectory("desktop/src/jvmTest/resources"))
        return assets;
    }

    override fun setupModule(module: Module) {
        // manually set, we do not show the second page with libraries
        starterContext.starter = starterContext.starterPack.starters.first()
        starterContext.starterDependencyConfig = loadDependencyConfig()[starterContext.starter?.id]

        super.setupModule(module)
    }

}
