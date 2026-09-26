// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

internal class PresentationSvnConfigurable(private val project: Project) : BoundSearchableConfigurable(
  SvnBundle.message("configurable.name.svn.presentation"),
  SvnConfigurable.HELP_ID + ".Presentation",
  SvnConfigurable.ID + ".Presentation"
), NoScroll {

  private lateinit var chMaxRevisions: JBCheckBox
  private lateinit var spMaxRevisions: JBIntSpinner

  override fun createPanel(): DialogPanel {
    val settings = SvnConfiguration.getInstance(project)

    return panel {
      row {
        checkBox(SvnBundle.message("settings.check.mergeinfo"))
          .bindSelected(settings::isCheckNestedForQuickMerge, settings::setCheckNestedForQuickMerge)
      }
      row {
        chMaxRevisions = checkBox(SvnBundle.message("settings.maximum.revisions.number"))
          .gap(RightGap.SMALL)
          .component
        spMaxRevisions = spinner(10..100000, 100)
          .enabledIf(chMaxRevisions.selected)
          .onReset {
            chMaxRevisions.isSelected = settings.maxAnnotateRevisions != -1
            spMaxRevisions.value = if (chMaxRevisions.isSelected) settings.maxAnnotateRevisions else SvnConfiguration.ourMaxAnnotateRevisionsDefault
          }.onApply {
            settings.maxAnnotateRevisions = getCurrentMaxAnnotateRevisions()
          }.onIsModified {
            getCurrentMaxAnnotateRevisions() != settings.maxAnnotateRevisions
          }
          .component
      }
      row {
        checkBox(SvnBundle.message("annotation.show.merge.sources.default.text"))
          .bindSelected(settings::isShowMergeSourcesInAnnotate, settings::setShowMergeSourcesInAnnotate)
      }
      row {
        checkBox(SvnBundle.message("svn.option.ignore.whitespace.in.annotate"))
          .bindSelected(settings::isIgnoreSpacesInAnnotate, settings::setIgnoreSpacesInAnnotate)
      }
    }
  }

  private fun getCurrentMaxAnnotateRevisions(): Int {
    return if (chMaxRevisions.isSelected) spMaxRevisions.number else -1
  }
}
