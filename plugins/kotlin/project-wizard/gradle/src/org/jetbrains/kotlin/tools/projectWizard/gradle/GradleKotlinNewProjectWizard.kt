// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.gradle

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.GRADLE
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import com.intellij.ui.layout.ValidationInfoBuilder
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.*
import org.jetbrains.kotlin.tools.projectWizard.compatibility.KotlinGradleCompatibilityStore
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType.GradleGroovyDsl
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType.GradleKotlinDsl
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.ProjectKind
import org.jetbrains.kotlin.tools.projectWizard.wizard.service.IdeaKotlinVersionProviderService
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep

internal class GradleKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = GRADLE

    override val ordinal = 200

    override fun createStep(parent: KotlinNewProjectWizard.Step): NewProjectWizardStep =
        Step(parent)
            //.nextStep(::AssetsStep)

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
        }

        override fun validateLanguageCompatibility(gradleVersion: GradleVersion): Boolean {
            val kotlinVersion = IdeaKotlinVersionProviderService().getKotlinVersion(ProjectKind.Singleplatform).version.text
            return KotlinGradleCompatibilityStore.kotlinVersionSupportsGradle(IdeKotlinVersion.get(kotlinVersion), gradleVersion)
        }

        override fun validateLanguageCompatibility(
            builder: ValidationInfoBuilder,
            gradleVersion: GradleVersion,
            withDialog: Boolean
        ): ValidationInfo? {
            if (validateLanguageCompatibility(gradleVersion)) return null
            val kotlinVersion = IdeaKotlinVersionProviderService().getKotlinVersion(ProjectKind.Singleplatform).version.text
            return builder.validationWithDialog(
                withDialog = withDialog,
                message = KotlinNewProjectWizardBundle.message(
                    "gradle.project.settings.distribution.version.kotlin.unsupported",
                    kotlinVersion,
                    gradleVersion.version
                ),
                dialogTitle = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.kotlin.title",
                    context.isCreatingNewProjectInt
                ),
                dialogMessage = KotlinNewProjectWizardBundle.message(
                    "gradle.settings.wizard.unsupported.kotlin.message",
                    kotlinVersion,
                    gradleVersion.version
                )
            )
        }

        override val distributionTypes: List<DistributionTypeItem> = listOf(DistributionTypeItem.WRAPPER)

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupGradleDistributionUI(builder)
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
                addSampleCode = addSampleCode,
                gradleVersion = gradleVersion
            )
        }
    }

    /* This step is temporarily disabled due to the Kotlin multiplatform wizard not using this step.
     * Instead, we will add the GradleW assets in a pipeline phase that is used by both the Kotlin new project
     * and Multiplatform wizard.
     * This should be reverted once the Kotlin Multiplatform wizard has been removed.
     */
    private class AssetsStep(parent: Step) : AssetsNewProjectWizardStep(parent) {

        override fun setupAssets(project: Project) {
            addAssets(StandardAssetsProvider().getGradlewAssets())
            if (context.isCreatingNewProject) {
                addAssets(StandardAssetsProvider().getGradleIgnoreAssets())
            }
        }
    }
}