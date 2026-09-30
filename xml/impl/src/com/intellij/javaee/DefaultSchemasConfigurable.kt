// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.xml.Html5SchemaProvider
import com.intellij.xml.XmlBundle
import com.intellij.xml.util.XmlUtil

class DefaultSchemasConfigurable(private val project: Project) :
  BoundConfigurable(XmlBundle.message("configurable.DefaultSchemasConfigurable.display.name"), "reference.default.schemas") {

  override fun createPanel(): DialogPanel {
    val manager = ExternalResourceManagerEx.getInstanceEx()
    val doctypeTextField: TextFieldWithAutoCompletion<String> = TextFieldWithAutoCompletion.create(
      project, ExternalResourceManager.getInstance().getResourceUrls(null, true).toList(), null, true, null)

    lateinit var html4RadioButton: JBRadioButton
    lateinit var html5RadioButton: JBRadioButton
    lateinit var otherRadioButton: JBRadioButton

    fun getDoctype(): String {
      return when {
        html4RadioButton.isSelected -> XmlUtil.XHTML4_SCHEMA_LOCATION
        html5RadioButton.isSelected -> Html5SchemaProvider.getHtml5SchemaLocation()
        else -> doctypeTextField.getText()
      }
    }

    return panel {
      buttonsGroup(XmlBundle.message("xml.schemas.border.title.default.html.language.level")) {
        row {
          html4RadioButton = radioButton(XmlBundle.message("xml.schemas.radio.button.html.4.http.www.w3.org.tr.html4.loose.dtd"))
            .component
        }
        row {
          html5RadioButton = radioButton(XmlBundle.message("xml.schemas.radio.button.html.5"))
            .component
        }
        row {
          otherRadioButton = radioButton(XmlBundle.message("xml.schemas.radio.button.other.doctype"))
            .gap(RightGap.SMALL)
            .component
          cell(doctypeTextField)
            .align(AlignX.FILL)
            .enabledIf(otherRadioButton.selected)
        }
      }

      onIsModified {
        manager.getDefaultHtmlDoctype(project) != getDoctype()
      }
      onReset {
        val doctype = manager.getDefaultHtmlDoctype(project)
        when {
          doctype.isEmpty() || doctype == XmlUtil.XHTML4_SCHEMA_LOCATION -> {
            html4RadioButton.isSelected = true
            doctypeTextField.text = ""
          }
          doctype == Html5SchemaProvider.getHtml5SchemaLocation() -> {
            html5RadioButton.isSelected = true
            doctypeTextField.text = ""
          }
          else -> {
            otherRadioButton.isSelected = true
            doctypeTextField.text = doctype
          }
        }
      }
      onApply {
        manager.setDefaultHtmlDoctype(getDoctype(), project)
      }

      buttonsGroup(XmlBundle.message("xml.schemas.border.title.xml.schema.version")) {
        row {
          radioButton(XmlBundle.message("xml.schemas.radio.button.xml.schema.1.0"),
                      ExternalResourceManagerEx.XMLSchemaVersion.XMLSchema_1_0)
        }
        row {
          radioButton(XmlBundle.message("xml.schemas.radio.button.xml.schema.1.1"),
                      ExternalResourceManagerEx.XMLSchemaVersion.XMLSchema_1_1)
        }
      }.bind({ manager.getXmlSchemaVersion(project) }, { manager.setXmlSchemaVersion(it, project) })
    }
  }
}
