// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleOnboardingTipsChangedEvent
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.generators.AssetsJavaNewProjectWizardStep
import com.intellij.ide.projectWizard.generators.AssetsJavaNewProjectWizardStep.Companion.proposeToGenerateOnboardingTipsByDefault
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizard
import com.intellij.ide.projectWizard.generators.BuildSystemJavaNewProjectWizardData
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi

class MavenJavaNewProjectWizard : BuildSystemJavaNewProjectWizard {

  override val name = MAVEN

  override val ordinal = 100

  override fun createStep(parent: JavaNewProjectWizard.Step): NewProjectWizardStep =
    Step(parent)
      .nextStep(::AssetsStep)

  class Step(parent: JavaNewProjectWizard.Step) :
    MavenNewProjectWizardStep<JavaNewProjectWizard.Step>(parent),
    BuildSystemJavaNewProjectWizardData by parent,
    MavenJavaNewProjectWizardData {

    override val addSampleCodeProperty = propertyGraph.property(true)
      .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)
    override val generateOnboardingTipsProperty = propertyGraph.property(proposeToGenerateOnboardingTipsByDefault())
      .bindBooleanStorage(NewProjectWizardStep.GENERATE_ONBOARDING_TIPS_NAME)

    override var addSampleCode by addSampleCodeProperty
    override var generateOnboardingTips by generateOnboardingTipsProperty

    private fun setupSampleCodeUI(builder: Panel) {
      builder.row {
        checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
          .bindSelected(addSampleCodeProperty)
          .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
      }
    }

    private fun setupSampleCodeWithOnBoardingTipsUI(builder: Panel) {
      builder.indent {
        row {
          checkBox(UIBundle.message("label.project.wizard.new.project.generate.onboarding.tips"))
            .bindSelected(generateOnboardingTipsProperty)
            .whenStateChangedFromUi { logAddSampleOnboardingTipsChangedEvent(it) }
        }
      }.enabledIf(addSampleCodeProperty)
    }

    override fun setupSettingsUI(builder: Panel) {
      setupJavaSdkUI(builder)
      setupParentsUI(builder)
      setupSampleCodeUI(builder)
      setupSampleCodeWithOnBoardingTipsUI(builder)
    }

    override fun setupAdvancedSettingsUI(builder: Panel) {
      setupGroupIdUI(builder)
      setupArtifactIdUI(builder)
    }

    override fun setupProject(project: Project) {
      linkMavenProject(project, MavenJavaModuleBuilder())
    }

    init {
      data.putUserData(MavenJavaNewProjectWizardData.KEY, this)
    }
  }

  private class AssetsStep(private val parent: Step) : AssetsJavaNewProjectWizardStep(parent) {

    override fun setupAssets(project: Project) {
      if (context.isCreatingNewProject) {
        addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
      }
      if (parent.addSampleCode) {
        withJavaSampleCodeAsset("src/main/java", parent.groupId, parent.generateOnboardingTips)
      }
    }

    override fun setupProject(project: Project) {
      if (parent.generateOnboardingTips) {
        prepareOnboardingTips(project)
      }
      super.setupProject(project)
    }
  }
}