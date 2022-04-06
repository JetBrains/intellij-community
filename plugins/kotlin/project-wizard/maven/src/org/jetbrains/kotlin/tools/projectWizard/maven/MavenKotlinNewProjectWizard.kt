// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logArtifactIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logGroupIdChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logParentChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkFinished
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logVersionChanged
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.GitNewProjectWizardData.Companion.gitData
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizardData
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.kmpWizardLink
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType

internal class MavenKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = "Maven"

    override val ordinal: Int = 200

    override fun createStep(parent: KotlinNewProjectWizard.Step): NewProjectWizardStep {
        return Step(parent).chain(::AssetsStep)
    }

    class Step(parent: KotlinNewProjectWizard.Step) :
        MavenNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent),
        BuildSystemKotlinNewProjectWizardData by parent {

        private val addSampleCodeProperty = propertyGraph.property(false)
        private val addSampleCode by addSampleCodeProperty

        override fun setupSettingsUI(builder: Panel) {
            super.setupSettingsUI(builder)
            with(builder) {
                row {
                    checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                        .bindSelected(addSampleCodeProperty)
                }.topGap(TopGap.SMALL)

                kmpWizardLink(context)
            }
        }

        override fun setupProject(project: Project) {
            KotlinNewProjectWizard.generateProject(
                project = project,
                projectPath = "$path/$name",
                projectName = name,
                sdk = sdk,
                buildSystemType = BuildSystemType.Maven,
                projectGroupId = groupId,
                artifactId = artifactId,
                version = version,
                addSampleCode = addSampleCode
            )

            logSdkFinished(sdk)
        }

        init {
            sdkProperty.afterChange { logSdkChanged(it) }
            parentProperty.afterChange { logParentChanged(!it.isPresent) }
            addSampleCodeProperty.afterChange { logAddSampleCodeChanged() }
            groupIdProperty.afterChange { logGroupIdChanged() }
            artifactIdProperty.afterChange { logArtifactIdChanged() }
            versionProperty.afterChange { logVersionChanged() }
        }
    }

    private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
        override fun setupAssets(project: Project) {
            outputDirectory = "$path/$name"
            if (gitData?.git == true) {
                addAssets(StandardAssetsProvider().getMavenIgnoreAssets())
            }
        }
    }
}
