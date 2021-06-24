// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemWithSettings
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import java.awt.Dimension
import java.awt.event.ItemListener
import javax.swing.JComponent

class KotlinNewProjectWizard : NewProjectWizard<KotlinSettings> {
  override val language: String = "Kotlin"
  override var settingsFactory = { KotlinSettings() }

  override fun settingsList(settings: KotlinSettings): List<LabelAndComponent> {
      var component: JComponent = JBLabel()
      panel {
          row {
              component = buttonSelector(settings.buildSystems.value, settings.buildSystemProperty) { it.name }.component
          }.largeGapAfter()
      }

      settings.propertyGraph.afterPropagation {
          settings.buildSystems.value.forEach { it.advancedSettings().apply { isVisible = false } }
          settings.buildSystemProperty.get().advancedSettings().apply { isVisible = true }
      }

      val sdkCombo = JdkComboBox(null, ProjectSdksModel(), null, null, null, null)
          .apply { minimumSize = Dimension(0, 0) }
          .also { combo -> combo.addItemListener(ItemListener { settings.sdk = combo.selectedJdk }) }

      settings.buildSystemProperty.set(settings.buildSystems.value.first())

      return listOf(
          LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.build.system")), component),
          LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), sdkCombo)
      ).plus(settings.buildSystems.value.map { LabelAndComponent(component = it.advancedSettings()) })
  }

  override fun setupProject(project: Project, settings: KotlinSettings, context: WizardContext) {
    settings.buildSystemProperty.get().setupProject(project, settings)
  }
}

class KotlinSettings {
    var sdk: Sdk? = null
    val propertyGraph: PropertyGraph = PropertyGraph()
    val buildSystems: Lazy<List<KotlinBuildSystemWithSettings<out Any?>>> = lazy {
        KotlinBuildSystemType.EP_NAME.extensionList.map { KotlinBuildSystemWithSettings(it) }
    }

    val buildSystemProperty: GraphProperty<KotlinBuildSystemWithSettings<*>> = propertyGraph.graphProperty {
        buildSystems.value.first()
    }
}

open class KotlinBuildSystemWithSettings<P>(val buildSystemType: KotlinBuildSystemType<P>) :
    BuildSystemWithSettings<KotlinSettings, P>(buildSystemType)