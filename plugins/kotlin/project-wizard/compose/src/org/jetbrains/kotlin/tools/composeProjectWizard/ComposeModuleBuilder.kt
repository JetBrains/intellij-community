// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.composeProjectWizard

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.starters.local.*
import com.intellij.ide.starters.local.wizard.StarterInitialStep
import com.intellij.ide.starters.shared.KOTLIN_STARTER_LANGUAGE
import com.intellij.ide.starters.shared.StarterLanguage
import com.intellij.ide.starters.shared.StarterProjectType
import com.intellij.ide.starters.shared.StarterTestRunner
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.lang.JavaVersion
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.tools.projectWizard.core.KotlinAssetsProvider
import javax.swing.Icon

internal class ComposeModuleBuilder : StarterModuleBuilder() {
    override fun getBuilderId(): String = "ComposeModuleBuilder"
    override fun getPresentableName(): String = ComposeProjectWizardBundle.message("module.presentation.name")
    override fun getWeight(): Int = KOTLIN_WEIGHT-2
    override fun getNodeIcon(): Icon = KotlinIcons.Wizard.COMPOSE
    override fun getDescription(): String = ComposeProjectWizardBundle.message("module.description")

    override fun getProjectTypes(): List<StarterProjectType> = emptyList()
    override fun getTestFrameworks(): List<StarterTestRunner> = emptyList()
    override fun getMinJavaVersion(): JavaVersion = LanguageLevel.JDK_11.toJavaVersion()

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

    override fun createWizardSteps(context: WizardContext, modulesProvider: ModulesProvider): Array<ModuleWizardStep> = emptyArray()

    override fun createOptionsStep(contextProvider: StarterContextProvider): StarterInitialStep {
        return ComposePWInitialStep(contextProvider)
    }

    override fun getTemplateProperties(): Map<String, Any> {
        return emptyMap()
    }

    override fun getAssets(starter: Starter): List<GeneratorAsset> {
        val ftManager = FileTemplateManager.getInstance(ProjectManager.getInstance().defaultProject)
        val standardAssetsProvider = StandardAssetsProvider()
        val assets = mutableListOf<GeneratorAsset>()

        assets.add(
            GeneratorTemplateFile(
                standardAssetsProvider.gradleWrapperPropertiesLocation,
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

        assets.addAll(standardAssetsProvider.getGradlewAssets())

        if (starterContext.isCreatingNewProject) {
            assets.addAll(KotlinAssetsProvider.getKotlinGradleIgnoreAssets())
        }

        assets.add(
            GeneratorTemplateFile(
                "build.gradle.kts",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_BUILD_GRADLE)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                ".run/desktop.run.xml",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_RUN_CONFIGURATION_XML)
            )
        )
        assets.add(
            GeneratorTemplateFile(
                "src/main/kotlin/Main.kt",
                ftManager.getCodeTemplate(ComposeModuleTemplateGroup.COMPOSE_DESKTOP_MAINKT)
            )
        )
        assets.add(GeneratorEmptyDirectory("src/main/resources"))
        assets.add(GeneratorEmptyDirectory("src/test/kotlin"))
        assets.add(GeneratorEmptyDirectory("src/test/resources"))
        return assets
    }

    override fun setupModule(module: Module) {
        // manually set, we do not show the second page with libraries
        starterContext.starter = starterContext.starterPack.starters.first()
        starterContext.starterDependencyConfig = loadDependencyConfig()[starterContext.starter?.id]

        super.setupModule(module)
    }

}
