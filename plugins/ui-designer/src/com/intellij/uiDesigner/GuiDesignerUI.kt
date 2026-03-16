// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class GuiDesignerUI(project: Project) {

  @JvmField
  val content = panel {
    val configuration = GuiDesignerConfiguration.getInstance(project)

    row {
      label(UIDesignerBundle.message("label.generate.gui.into"))
        .align(AlignY.TOP)

      lateinit var rbInstrumentClasses: JBRadioButton
      lateinit var rbInstrumentSourcesOnCompilation: JBRadioButton
      lateinit var rbInstrumentSourcesOnSave: JBRadioButton
      panel {
        buttonsGroup {
          row {
            rbInstrumentClasses = radioButton(UIDesignerBundle.message("radio.generate.into.class"))
              .component
          }
          row {
            rbInstrumentSourcesOnCompilation = radioButton(UIDesignerBundle.message("radio.generate.into.java.on.compilation"))
              .component
          }
          row {
            rbInstrumentSourcesOnSave = radioButton(UIDesignerBundle.message("radio.generate.into.java.on.save"))
              .component
          }
        }
      }.onApply {
        configuration.INSTRUMENT_CLASSES = rbInstrumentClasses.isSelected
        configuration.GENERATE_SOURCES_ON_SAVE = rbInstrumentSourcesOnSave.isSelected

      }.onIsModified {
        configuration.INSTRUMENT_CLASSES != rbInstrumentClasses.isSelected ||
        configuration.GENERATE_SOURCES_ON_SAVE != rbInstrumentSourcesOnSave.isSelected
      }.onReset {
        if (configuration.INSTRUMENT_CLASSES) {
          rbInstrumentClasses.setSelected(true)
        }
        else if (configuration.GENERATE_SOURCES_ON_SAVE) {
          rbInstrumentSourcesOnSave.setSelected(true)
        }
        else {
          rbInstrumentSourcesOnCompilation.setSelected(true)
        }
      }
    }

    row {
      checkBox(UIDesignerBundle.message("chk.copy.form.runtime"))
        .bindSelected(configuration::COPY_FORMS_RUNTIME_TO_OUTPUT)
    }

    row {
      checkBox(UIDesignerBundle.message("chk.generate.final.fields"))
        .bindSelected(configuration::GENERATE_SOURCES_FINAL_FIELDS)
    }

    row(UIDesignerBundle.message("default.layout.manager")) {
      comboBox(LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames().toList(),
               textListCellRenderer("", LayoutManagerRegistry::getLayoutManagerDisplayName))
        .bindItem(configuration::DEFAULT_LAYOUT_MANAGER)
    }

    row(UIDesignerBundle.message("default.field.accessibility")) {
      comboBox(listOf("private", "package-private", "protected", "public"))
        .bindItem(configuration::DEFAULT_FIELD_ACCESSIBILITY)
    }

    row {
      checkBox(UIDesignerBundle.message("ui.designer.general.settings.resize.column.and.row.headers"))
        .bindSelected(configuration::RESIZE_HEADERS)
    }

    row {
      checkBox(UIDesignerBundle.message("ui.designer.general.settings.use.dynamic.bundles"))
        .bindSelected(configuration::USE_DYNAMIC_BUNDLES)
    }
  }
}