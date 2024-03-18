// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemChanged
import com.intellij.ide.projectWizard.NewProjectWizardCollector.BuildSystem.logBuildSystemFinished
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Language.KOTLIN
import com.intellij.ide.wizard.*
import com.intellij.ide.wizard.LanguageNewProjectWizardData.Companion.languageData
import com.intellij.ide.wizard.language.LanguageGeneratorNewProjectWizard
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.*
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.tools.projectWizard.core.Context
import org.jetbrains.kotlin.tools.projectWizard.core.service.WizardKotlinVersion
import org.jetbrains.kotlin.tools.projectWizard.plugins.kotlin.KotlinPlugin
import org.jetbrains.kotlin.tools.projectWizard.wizard.NewProjectWizardModuleBuilder

class KotlinNewProjectWizard : LanguageGeneratorNewProjectWizard {

    override val name = KOTLIN

    override val icon = KotlinIcons.SMALL_LOGO

    override val ordinal = 100

    companion object {
        private fun Context.Reader.getWizardKotlinVersion(): WizardKotlinVersion {
            return KotlinPlugin.version.propertyValue
        }

        fun getKotlinWizardVersion(newProjectWizardModuleBuilder: NewProjectWizardModuleBuilder): WizardKotlinVersion {
            var wizardKotlinVersion: WizardKotlinVersion
            newProjectWizardModuleBuilder.apply {
                wizardKotlinVersion = wizard.context.Reader().getWizardKotlinVersion()
            }
            return wizardKotlinVersion
        }
    }

    // Uncommenting this line disables new Kotlin modules
    //override fun isEnabled(context: WizardContext): Boolean = context.isCreatingNewProject

    override fun createStep(parent: NewProjectWizardStep) = Step(parent)

    class Step(parent: NewProjectWizardStep) :
        AbstractNewProjectWizardMultiStep<Step, BuildSystemKotlinNewProjectWizard>(parent, BuildSystemKotlinNewProjectWizard.EP_NAME),
        LanguageNewProjectWizardData by parent.languageData!!,
        BuildSystemKotlinNewProjectWizardData {

        override val self = this
        override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")
        override val buildSystemProperty by ::stepProperty
        override var buildSystem by ::step

        override fun createAndSetupSwitcher(builder: Row): SegmentedButton<String> {
            return super.createAndSetupSwitcher(builder)
                .whenItemSelectedFromUi { logBuildSystemChanged() }
        }

        override fun setupProject(project: Project) {
            super.setupProject(project)

            logBuildSystemFinished()
        }

        init {
            data.putUserData(BuildSystemKotlinNewProjectWizardData.KEY, this)
        }
    }
}