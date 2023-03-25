// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.gradle

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.setupKmpWizardLinkUI
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType.GradleGroovyDsl
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType.GradleKotlinDsl
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep

internal class GradleKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = GRADLE

    override val ordinal = 200

    override fun createStep(parent: KotlinNewProjectWizard.Step): NewProjectWizardStep =
        Step(parent)
            .nextStep(::AssetsStep)

    class Step(parent: KotlinNewProjectWizard.Step) :
        GradleNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent),
        BuildSystemKotlinNewProjectWizardData by parent {

        private val addSampleCodeProperty = propertyGraph.property(true)
            .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

        private val addSampleCode by addSampleCodeProperty

        private fun setupSampleCodeUI(builder: Panel) {
            builder.row {
                checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                    .bindSelected(addSampleCodeProperty)
                    .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
            }
        }

        override fun setupSettingsUI(builder: Panel) {
            setupJavaSdkUI(builder)
            setupGradleDslUI(builder)
            setupParentsUI(builder)
            setupSampleCodeUI(builder)
            setupKmpWizardLinkUI(builder)
        }

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupGroupIdUI(builder)
            setupArtifactIdUI(builder)
        }

        override fun setupProject(project: Project) {
            KotlinNewProjectWizard.generateProject(
                project = project,
                projectPath = parentStep.path + "/" + parentStep.name,
                projectName = parentStep.name,
                sdk = sdk,
                buildSystemType = when (gradleDsl) {
                    GradleDsl.KOTLIN -> GradleKotlinDsl
                    GradleDsl.GROOVY -> GradleGroovyDsl
                },
                projectGroupId = groupId,
                artifactId = artifactId,
                version = version,
                addSampleCode = addSampleCode
            )
        }
    }

    private class AssetsStep(parent: Step) : AssetsNewProjectWizardStep(parent) {

        override fun setupAssets(project: Project) {
            addAssets(StandardAssetsProvider().getGradlewAssets())
            if (context.isCreatingNewProject) {
                addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
            }
        }
    }
}