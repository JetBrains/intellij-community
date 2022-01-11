// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.wizards.archetype

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalog
import org.jetbrains.idea.maven.indices.arhetype.MavenCatalogManager
import org.jetbrains.idea.maven.wizards.MavenWizardBundle

class MavenEditCatalogDialog(project: Project, private val initialCatalog: MavenCatalog) : AbstractMavenCatalogDialog(project) {

  override fun onApply() {
    val catalog = getCatalog() ?: return
    val catalogManager = MavenCatalogManager.getInstance()
    catalogManager.removeCatalog(initialCatalog)
    catalogManager.addCatalog(catalog)
  }

  init {
    name = initialCatalog.name
    location = initialCatalog.location
  }

  init {
    title = MavenWizardBundle.message("maven.new.project.wizard.archetype.catalog.edit.dialog.title")
    init()
  }
}