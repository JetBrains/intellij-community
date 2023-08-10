// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.maven.indices.archetype.MavenCatalogManager
import org.jetbrains.idea.maven.wizards.MavenWizardBundle

class MavenCatalogsConfigurable(private val project: Project) : BoundSearchableConfigurable(
  MavenWizardBundle.message("maven.configurable.archetype.catalog.title"),
  "reference.settings.project.maven.archetype.catalogs",
  "reference.settings.project.maven.archetype.catalogs"
) {

  override fun createPanel() = panel {
    val catalogsManager = MavenCatalogManager.getInstance()
    val table = MavenCatalogsTable(project)
    row {
      label(MavenWizardBundle.message("maven.configurable.archetype.catalog.label"))
    }
    row {
      cell(table.component)
        .align(Align.FILL)
        .bind(
          { table.catalogs },
          { _, it -> table.catalogs = it },
          MutableProperty(
            { catalogsManager.getCatalogs(project) },
            { catalogsManager.setCatalogs(it) }
          )
        )
    }.resizableRow()
  }
}