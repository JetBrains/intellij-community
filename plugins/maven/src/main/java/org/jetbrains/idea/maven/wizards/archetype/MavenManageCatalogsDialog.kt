// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.wizards.MavenWizardBundle

class MavenManageCatalogsDialog(private val project: Project) : DialogWrapper(project, true) {

  override fun createCenterPanel() = panel {
    val catalogsManager = MavenCatalogManager.getInstance()
    val table = MavenCatalogsTable(project)
    table.catalogs = catalogsManager.getCatalogs(project)
    row {
      cell(table.component)
        .horizontalAlign(HorizontalAlign.FILL)
        .verticalAlign(VerticalAlign.FILL)
    }.resizableRow()
    onApply {
      catalogsManager.setCatalogs(table.catalogs)
    }
  }

  init {
    title = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.manage.dialog.title")
    init()
  }
}