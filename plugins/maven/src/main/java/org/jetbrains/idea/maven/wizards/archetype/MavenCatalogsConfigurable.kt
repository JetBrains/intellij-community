// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.layout.*
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.wizards.MavenWizardBundle

class MavenCatalogsConfigurable(private val project: Project) : BoundConfigurable(
  MavenWizardBundle.message("maven.configurable.archetype.catalog.title"),
  helpTopic = "reference.settings.project.maven.archetype.catalogs"
) {

  override fun createPanel() = panel {
    val catalogsManager = MavenCatalogManager.getInstance()
    val table = MavenCatalogsTable(project)
    row {
      label(MavenWizardBundle.message("maven.configurable.archetype.catalog.label"))
    }
    row {
      cell(table.component)
        .horizontalAlign(HorizontalAlign.FILL)
        .verticalAlign(VerticalAlign.FILL)
        .bind(
          { table.catalogs },
          { _, it -> table.catalogs = it },
          PropertyBinding(
            { catalogsManager.getCatalogs(project) },
            { catalogsManager.setCatalogs(it) }
          )
        )
    }.resizableRow()
  }
}