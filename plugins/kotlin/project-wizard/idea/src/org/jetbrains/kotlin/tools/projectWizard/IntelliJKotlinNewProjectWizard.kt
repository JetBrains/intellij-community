// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.ui.UIBundle.*
import com.intellij.ui.dsl.builder.*
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType

internal class IntelliJKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = "IntelliJ"

    override fun createStep(parent: KotlinNewProjectWizard.Step) = object : AbstractNewProjectWizardStep(parent) {
        private val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
        private val addSampleCodeProperty = propertyGraph.graphProperty { false }

        private val sdk by sdkProperty
        private val addSampleCode by addSampleCodeProperty

        override fun setupUI(builder: Panel) {
            with(builder) {
                row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
                    val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
                    sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
                        .columns(COLUMNS_MEDIUM)
                }
                row {
                    checkBox(message("label.project.wizard.new.project.add.sample.code"))
                        .bindSelected(addSampleCodeProperty)
                }.topGap(TopGap.SMALL)
            }
        }

        override fun setupProject(project: Project) =
            KotlinNewProjectWizard.generateProject(
                project = project,
                projectPath = parent.projectPath.systemIndependentPath,
                projectName = parent.name,
                sdk = sdk,
                buildSystemType = BuildSystemType.Jps,
                addSampleCode = addSampleCode
            )
    }
}