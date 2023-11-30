// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.emmet

import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import com.intellij.xml.XmlBundle

class XmlEmmetConfigurable : BoundSearchableConfigurable(XmlBundle.message("configurable.XmlEmmetConfigurable.display.name"),
                                                         "reference.idesettings.emmet.xml") {

  private val filtersCheckBoxes = mutableMapOf<String, JBCheckBox>()

  override fun createPanel(): DialogPanel {
    return panel {
      val settings = EmmetOptions.getInstance()
      lateinit var cbEmmetEnabled: JBCheckBox
      row {
        cbEmmetEnabled = checkBox(XmlBundle.message("emmet.enable.label"))
          .bindSelected(settings::isEmmetEnabled, settings::setEmmetEnabled)
          .component
      }
      rowsRange {
        row {
          checkBox(XmlBundle.message("emmet.enable.preview"))
            .bindSelected(settings::isPreviewEnabled, settings::setPreviewEnabled)
        }
        row {
          checkBox(XmlBundle.message("emmet.href.autodetect"))
            .bindSelected(settings::isHrefAutoDetectEnabled, settings::setHrefAutoDetectEnabled)
        }
        row {
          checkBox(XmlBundle.message("emmet.add.edit.point.at.the.end.of.template"))
            .bindSelected(settings::isAddEditPointAtTheEndOfTemplate, settings::setAddEditPointAtTheEndOfTemplate)
        }

        group(XmlBundle.message("xml.options.border.title.bem")) {
          row(XmlBundle.message("emmet.bem.class.name.element.separator.label")) {
            textField()
              .bindText(settings::getBemElementSeparator, settings::setBemElementSeparator)
              .columns(COLUMNS_TINY)
          }
          row(XmlBundle.message("emmet.bem.class.name.modifier.separator.label")) {
            textField()
              .bindText(settings::getBemModifierSeparator, settings::setBemModifierSeparator)
              .columns(COLUMNS_TINY)
          }
          row(XmlBundle.message("emmet.bem.short.element.prefix.label")) {
            textField()
              .bindText(settings::getBemShortElementPrefix, settings::setBemShortElementPrefix)
              .columns(COLUMNS_TINY)
          }
        }

        group(XmlBundle.message("emmet.filters.enabled.by.default")) {
          val filters = ZenCodingFilter.getInstances()
          for (filter in filters) {
            if (filter.isSystem || filtersCheckBoxes.containsKey(filter.getSuffix())) {
              continue
            }

            row {
              val checkBox = checkBox(filter.getDisplayName()).component
              filtersCheckBoxes[filter.getSuffix()] = checkBox
            }
          }
        }
      }.enabledIf(cbEmmetEnabled.selected)
    }
  }

  override fun disposeUIResources() {
    filtersCheckBoxes.clear()
    super.disposeUIResources()
  }

  override fun isModified(): Boolean {
    return super.isModified() || EmmetOptions.getInstance().filtersEnabledByDefault != enabledFilters()
  }

  override fun apply() {
    super.apply()
    EmmetOptions.getInstance().filtersEnabledByDefault = enabledFilters()
  }

  override fun reset() {
    super.reset()

    val enabledByDefault = EmmetOptions.getInstance().filtersEnabledByDefault
    for (filter in ZenCodingFilter.getInstances()) {
      if (filter.isSystem) continue
      filtersCheckBoxes[filter.getSuffix()]?.setSelected(enabledByDefault.contains(filter.getSuffix()))
    }
  }

  private fun enabledFilters(): Set<String> {
    return filtersCheckBoxes.filterValues { it.isSelected }.keys
  }
}
