// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.MAVEN
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStep.Companion.ADD_SAMPLE_CODE_PROPERTY_NAME
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.whenStateChangedFromUi
import com.intellij.util.io.createDirectories
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard.Companion.DEFAULT_KOTLIN_VERSION
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.KOTLIN_SAMPLE_FILE_TEMPLATE_NAME
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.PACKAGE_NAME_KEY
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SOURCE_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_MAIN_KOTLIN_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_MAIN_RESOURCES_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_TEST_KOTLIN_PATH
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData.Companion.SRC_TEST_RESOURCES_PATH
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard.Companion.getKotlinWizardVersion
import org.jetbrains.kotlin.tools.projectWizard.moduleConfigurators.TargetJvmVersion
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder
import java.nio.file.Path

internal class MavenKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = MAVEN

    override val ordinal = 100

    override fun createStep(parent: KotlinNewProjectWizard.Step): NewProjectWizardStep =
        Step(parent)
            .nextStep(::AssetsStep)

    class Step(parent: KotlinNewProjectWizard.Step) :
        MavenNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent),
        BuildSystemKotlinNewProjectWizardData by parent
    {

        private val addSampleCodeProperty = propertyGraph.property(true)
            .bindBooleanStorage(ADD_SAMPLE_CODE_PROPERTY_NAME)

        val addSampleCode by addSampleCodeProperty

        private fun setupSampleCodeUI(builder: Panel) {
            builder.row {
                checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                    .bindSelected(addSampleCodeProperty)
                    .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
            }
        }

        override fun setupSettingsUI(builder: Panel) {
            setupJavaSdkUI(builder)
            setupParentsUI(builder)
            setupSampleCodeUI(builder)
        }

        override fun setupAdvancedSettingsUI(builder: Panel) {
            setupGroupIdUI(builder)
            setupArtifactIdUI(builder)
        }

        private fun findKotlinVersionToUse(newProjectWizardModuleBuilder: NewProjectWizardModuleBuilder) {
            kotlinPluginWizardVersion = getKotlinWizardVersion(newProjectWizardModuleBuilder).version.text
        }

        private fun initializeProjectValues() {
            findSelectedJvmTarget()
            findKotlinVersionToUse(NewProjectWizardModuleBuilder())
        }

        private var selectedJdkJvmTarget: String = TargetJvmVersion.JVM_1_8.value
        private var kotlinPluginWizardVersion: String = DEFAULT_KOTLIN_VERSION

        private fun findSelectedJvmTarget() {
            val sdkNumber = sdk?.let { JavaSdk.getInstance().getVersion(it) }?.ordinal
            // Fix 8 to a smaller value if we inherit (from parent pom.xml) Kotlin versions that support earlier JVM targets KTIJ-27487
            if (sdkNumber == null || sdkNumber <= 8) { // For 1.8 we need a special form – 1.8 unlike, for example, just 9 and higher
                selectedJdkJvmTarget = TargetJvmVersion.JVM_1_8.value
            } else {
                selectedJdkJvmTarget = sdkNumber.toString()
            }
        }

        override fun setupProject(project: Project) {
            initializeProjectValues()

            ExternalProjectsManagerImpl.setupCreatedProject(project)
            project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true)

            val moduleBuilder = MavenKotlinModuleBuilder()

            linkMavenProject(
                project,
                moduleBuilder
            ) { builder ->
                builder.jvmTargetVersion = selectedJdkJvmTarget
                builder.kotlinPluginWizardVersion = kotlinPluginWizardVersion
            }
        }
    }

    private class AssetsStep(private val parent: Step) : AssetsNewProjectWizardStep(parent) {

        override fun setupAssets(project: Project) {
            if (context.isCreatingNewProject) {
                addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
            }
            createKotlinContentRoots()
            if (parent.addSampleCode) {
                withKotlinSampleCode(parent.groupId)
            }
        }

        private fun createKotlinContentRoots() {
            val directories = listOf(
                "$outputDirectory$SRC_MAIN_KOTLIN_PATH",
                "$outputDirectory$SRC_MAIN_RESOURCES_PATH",
                "$outputDirectory$SRC_TEST_KOTLIN_PATH",
                "$outputDirectory$SRC_TEST_RESOURCES_PATH",
            )
            directories.forEach {
                Path.of(it).createDirectories()
            }
        }

        private fun withKotlinSampleCode(packageName: String) {
            addTemplateAsset(SOURCE_PATH, KOTLIN_SAMPLE_FILE_TEMPLATE_NAME, buildMap {
                put(PACKAGE_NAME_KEY, packageName)
            })
            addFilesToOpen(SOURCE_PATH)
        }
    }
}
