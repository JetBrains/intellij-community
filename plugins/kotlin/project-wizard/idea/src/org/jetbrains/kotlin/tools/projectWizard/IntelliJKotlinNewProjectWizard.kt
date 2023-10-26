// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logUseCompactProjectStructureChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.INTELLIJ
import com.intellij.ide.projectWizard.generators.IntelliJNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType
import org.jetbrains.kotlin.tools.projectWizard.wizard.AssetsKotlinNewProjectWizardStep
import org.jetbrains.kotlin.tools.projectWizard.wizard.KotlinNewProjectWizardUIBundle

internal class IntelliJKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    companion object {
        const val USE_COMPACT_PROJECT_STRUCTURE_NAME: String = "NewProjectWizard.useCompactProjectStructure"
    }

    override val name = INTELLIJ

    override val ordinal = 0

    override fun createStep(parent: KotlinNewProjectWizard.Step): NewProjectWizardStep =
        Step(parent)
            .nextStep(::AssetsStep)

    class Step(parent: KotlinNewProjectWizard.Step) :
        IntelliJNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent),
        BuildSystemKotlinNewProjectWizardData by parent {

        private val useCompactProjectStructureProperty = propertyGraph.property(true)
            .bindBooleanStorage(USE_COMPACT_PROJECT_STRUCTURE_NAME)

        var useCompactProject by useCompactProjectStructureProperty

        override fun setupSettingsUI(builder: Panel) {
            setupJavaSdkUI(builder)
            setupSampleCodeUI(builder)
            setupSampleCodeWithOnBoardingTipsUI(builder)
            setupCompactDirectoryLayoutUI(builder)
        }

        private fun setupCompactDirectoryLayoutUI(builder: Panel) {
            builder.row {
                checkBox(KotlinNewProjectWizardUIBundle.message("label.project.wizard.new.project.use.compact.project.structure"))
                    .bindSelected(useCompactProjectStructureProperty)
                    .whenStateChangedFromUi { logUseCompactProjectStructureChanged(it) }
                    .gap(RightGap.SMALL)
                contextHelp(KotlinNewProjectWizardUIBundle.message("tooltip.project.wizard.new.project.use.compact.project.structure"))
            }
        }

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupModuleNameUI(builder)
            setupModuleContentRootUI(builder)
            setupModuleFileLocationUI(builder)
        }

        override fun setupProject(project: Project) {
            KotlinNewProjectWizard.generateProject(
                project = project,
                projectPath = "$path/$name",
                projectName = name,
                isProject = context.isCreatingNewProject,
                sdk = sdk,
                buildSystemType = BuildSystemType.Jps,
                addSampleCode = false,
                useCompactProjectStructure = useCompactProject
            )
        }
    }

    private class AssetsStep(private val parent: Step) : AssetsKotlinNewProjectWizardStep(parent) {
        private fun shouldAddOnboardingTips(): Boolean = parent.addSampleCode && parent.generateOnboardingTips

        override fun setupAssets(project: Project) {
            if (context.isCreatingNewProject) {
                addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
            }
            if (parent.addSampleCode) {
                val sourceRootPath = if (parent.useCompactProject) "src" else "src/main/kotlin"
                withKotlinSampleCode(sourceRootPath, null, shouldAddOnboardingTips())
            }
        }

        override fun setupProject(project: Project) {
            if (shouldAddOnboardingTips()) {
                prepareOnboardingTips(project)
            }
            super.setupProject(project)
        }
    }
}