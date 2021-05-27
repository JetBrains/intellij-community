// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent

class KotlinNewProjectWizard : NewProjectWizard<KotlinSettings> {
  override val language: String = "Kotlin"
  override var settingsFactory = { KotlinSettings() }

  private fun getProjectTemplates() = listOf(
    NewProjectTemplate("Console application"),
    NewProjectTemplate("Frontend"),
    NewProjectTemplate("Full-stack web"),
    NewProjectTemplate("Multiplatform"),
    NewProjectTemplate("Multiplatform mobile"),
    NewProjectTemplate("Native"))

  override fun settingsList(settings: KotlinSettings): List<LabelAndComponent> {
    val templateList = JBList(getProjectTemplates()).apply {
      cellRenderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value.name }
      border = JBUI.Borders.customLine(JBColor.border())
      addListSelectionListener { settings.template = selectedValue }
    }

    val buildSystemButtons = KotlinBuildSystemType.EP_NAME.extensionList
    var component: JComponent? = null
    panel {
      row {
        val property: GraphProperty<KotlinBuildSystemType> = settings.buildSystemProperty
        component = buttonSelector(buildSystemButtons, property) { it.name }.component
      }
    }

    return listOf(
        LabelAndComponent(
            JBLabel(KotlinNewProjectWizardBundle.message("label.project.wizard.new.project.templates")), templateList),
            LabelAndComponent(JBLabel(KotlinNewProjectWizardBundle.message("label.project.wizard.new.project.build.system")), component!!),
            LabelAndComponent(JBLabel(KotlinNewProjectWizardBundle.message("label.project.wizard.new.project.jdk")),
                        JdkComboBox(null, ProjectSdksModel(), null, null, null, null)
        )
    )
  }

  override fun setupProject(project: Project?, settings: KotlinSettings, context: WizardContext) {
    settings.buildSystemProperty.get().setupProject(settings)
  }
}

class NewProjectTemplate(@Nls val name: String, val icon: Icon? = null)

class KotlinSettings {
    var template = NewProjectTemplate("Console application")

    val propertyGraph: PropertyGraph = PropertyGraph()
    val buildSystemProperty: GraphProperty<KotlinBuildSystemType> = propertyGraph.graphProperty {
        KotlinBuildSystemType.EP_NAME.extensions.first()
    }
}