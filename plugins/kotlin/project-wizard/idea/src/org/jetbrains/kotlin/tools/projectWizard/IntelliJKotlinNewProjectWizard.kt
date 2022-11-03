// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logAddSampleCodeChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logSdkChanged
import com.intellij.ide.projectWizard.NewProjectWizardConstants.BuildSystem.INTELLIJ
import com.intellij.ide.projectWizard.generators.AssetsNewProjectWizardStep
import com.intellij.ide.starters.local.StandardAssetsProvider
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.name
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.path
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.chain
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.util.bindBooleanStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.ui.UIBundle.*
import com.intellij.ui.dsl.builder.*
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType

internal class IntelliJKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = INTELLIJ

    override val ordinal = 0

    override fun createStep(parent: KotlinNewProjectWizard.Step) = Step(parent).chain(::AssetsStep)

    class Step(parent: KotlinNewProjectWizard.Step) :
        AbstractNewProjectWizardStep(parent),
        BuildSystemKotlinNewProjectWizardData by parent {

        private val sdkProperty = propertyGraph.property<Sdk?>(null)
        private val addSampleCodeProperty = propertyGraph.property(true)
            .bindBooleanStorage("NewProjectWizard.addSampleCodeState")

        private val sdk by sdkProperty
        private val addSampleCode by addSampleCodeProperty

        override fun setupUI(builder: Panel) {
            with(builder) {
                row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
                    val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
                    sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
                        .columns(COLUMNS_MEDIUM)
                        .whenItemSelectedFromUi { logSdkChanged(sdk) }
                }
                row {
                    checkBox(message("label.project.wizard.new.project.add.sample.code"))
                        .bindSelected(addSampleCodeProperty)
                        .whenStateChangedFromUi { logAddSampleCodeChanged(it) }
                }.topGap(TopGap.SMALL)

                kmpWizardLink(context)
            }
        }

        override fun setupProject(project: Project) =
            KotlinNewProjectWizard.generateProject(
                project = project,
                projectPath = "$path/$name",
                projectName = name,
                sdk = sdk,
                buildSystemType = BuildSystemType.Jps,
                addSampleCode = addSampleCode
            )
    }

    private class AssetsStep(parent: NewProjectWizardStep) : AssetsNewProjectWizardStep(parent) {
        override fun setupAssets(project: Project) {
            outputDirectory = "$path/$name"
            addAssets(StandardAssetsProvider().getIntelliJIgnoreAssets())
        }
    }
}