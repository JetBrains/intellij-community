// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.gradle

import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType.GradleGroovyDsl
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType.GradleKotlinDsl
import org.jetbrains.plugins.gradle.service.project.wizard.GradleNewProjectWizardStep

internal class GradleKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = "Gradle"

    override fun createStep(parent: KotlinNewProjectWizard.Step) = object : GradleNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent) {
        private val addSampleCodeProperty = propertyGraph.graphProperty { false }
        private val addSampleCode by addSampleCodeProperty

        init {
            useKotlinDsl = true
        }

        override fun setupUI(builder: Panel) {
            super.setupUI(builder)
            with(builder) {
                row {
                    checkBox(UIBundle.message("label.project.wizard.new.project.add.sample.code"))
                        .bindSelected(addSampleCodeProperty)
                }.topGap(TopGap.SMALL)
            }
        }

        override fun setupProject(project: Project) = KotlinNewProjectWizard.generateProject(
            project = project,
            projectPath = parent.projectPath.systemIndependentPath,
            projectName = parent.name,
            sdk = sdk,
            buildSystemType = if (useKotlinDsl) GradleKotlinDsl else GradleGroovyDsl,
            projectGroupId = groupId,
            artifactId = artifactId,
            version = version,
            addSampleCode = addSampleCode
        )
    }
}