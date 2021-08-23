// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.*
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemWithSettings
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Key
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import java.awt.Dimension
import java.awt.event.ItemListener
import javax.swing.JComponent

class KotlinNewProjectWizard : NewProjectWizard<KotlinSettings> {
    override val language: String = "Kotlin"

    override val settingsKey = KotlinSettings.KEY
    override fun createSettings() = KotlinSettings()

    override fun settingsList(settings: KotlinSettings, context: WizardContext): List<SettingsComponent> {
        val buildSystemsSettings = KotlinBuildSystemType.EP_NAME.extensionList
            .map { KotlinBuildSystemWithSettings(it) }
            .map { KotlinBuildSystemSettingsComponent(it, it.advancedSettings(context)) }
        val buildSystemsActions = buildSystemsSettings
            .map { ButtonSelectorAction(it, settings.buildSystemProperty, it.settings.name) }
        val buildSystemsActionGroup = DefaultActionGroup(buildSystemsActions)
        val buildSystemsToolbar = ButtonSelectorToolbar("ButtonSelector", buildSystemsActionGroup, true)
        buildSystemsToolbar.targetComponent = null
        val buildSystemsButtons = buildSystemsToolbar.component

        settings.propertyGraph.afterPropagation {
            buildSystemsSettings.forEach { it.component.isVisible = false }
            settings.buildSystem.component.isVisible = true
        }
        settings.buildSystem = buildSystemsSettings.first()

        val sdkModel = ProjectSdksModel().also { it.syncSdks() }
        val sdkCombo = JdkComboBox(null, sdkModel, null, null, null, null)
            .apply { minimumSize = Dimension(0, 0) }
            .also { combo -> combo.addItemListener(ItemListener { settings.sdk = combo.selectedJdk }) }
            .also { combo ->
                val defaultProject = ProjectManager.getInstance().defaultProject
                val defaultProjectSdk = ProjectRootManager.getInstance(defaultProject).projectSdk
                if (defaultProjectSdk != null) {
                    combo.selectedJdk = defaultProjectSdk
                }
            }

        return listOf(
            LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.build.system")), buildSystemsButtons),
            LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), sdkCombo)
        ).plus(buildSystemsSettings.map { JustComponent(it.component) })
    }

    override fun setupProject(project: Project, settings: KotlinSettings, context: WizardContext) {
        settings.buildSystem.settings.setupProject(project, context)

        settings.sdk?.let { sdk ->
            val table = ProjectJdkTable.getInstance()
            runWriteAction {
                if (table.findJdk(sdk.name) == null) table.addJdk(sdk)
                ProjectRootManager.getInstance(project).projectSdk = sdk
            }
        }
    }
}

open class KotlinBuildSystemWithSettings<P>(val buildSystemType: KotlinBuildSystemType<P>) :
    BuildSystemWithSettings<P>(buildSystemType)

class KotlinBuildSystemSettingsComponent<S>(
    val settings: KotlinBuildSystemWithSettings<S>,
    val component: JComponent
)

class KotlinSettings {
    val propertyGraph: PropertyGraph = PropertyGraph()

    val buildSystemProperty = propertyGraph.graphProperty<KotlinBuildSystemSettingsComponent<*>> {
        throw UninitializedPropertyAccessException()
    }

    var sdk: Sdk? = null
    var buildSystem by buildSystemProperty

    companion object {
        val KEY = Key.create<KotlinSettings>(KotlinSettings::class.java.name)
    }
}