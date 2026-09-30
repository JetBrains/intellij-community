// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.xml.XmlBundle

class XMLCatalogConfigurable(private val project: Project?) :
  BoundConfigurable(XmlBundle.message("configurable.XMLCatalogConfigurable.display.name"), "XML.Catalog.Dialog") {

  override fun createPanel(): DialogPanel {
    val settings = ExternalResourceManagerEx.getInstanceEx()

    return panel {
      row(XmlBundle.message("xml.options.label.catalog.property.file")) {
        textFieldWithBrowseButton(
          FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle(XmlBundle.message("xml.catalog.properties.file"))
            .withEnvironmentRestricted(true)
            .withLocalFileSystem(),
          project)
          .bindText({ settings.getCatalogPropertiesFile().orEmpty() }, settings::setCatalogPropertiesFile)
          .align(AlignX.FILL)
      }
    }
  }
}
