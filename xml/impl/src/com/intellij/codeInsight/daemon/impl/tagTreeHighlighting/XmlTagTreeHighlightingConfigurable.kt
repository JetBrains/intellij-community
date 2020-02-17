/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.tagTreeHighlighting

import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.options.UiDslConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.layout.*
import com.intellij.xml.XmlBundle
import com.intellij.xml.breadcrumbs.BreadcrumbsPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class XmlTagTreeHighlightingConfigurable : UiDslConfigurable.Simple() {
  override fun RowBuilder.createComponentRow() {
    val options = WebEditorOptions.getInstance()
    row {
      val enable = checkBox(XmlBundle.message("settings.enable.html.xml.tag.tree.highlighting"),
                            options::isTagTreeHighlightingEnabled, options::setTagTreeHighlightingEnabled)
        .onApply { clearTagTreeHighlighting() }

      val spinnerGroupName = "xml.tag.highlight.spinner"
      row(XmlBundle.message("settings.levels.to.highlight")) {
        spinner({ options.tagTreeHighlightingLevelCount }, { options.tagTreeHighlightingLevelCount = it },
                1, 50, 1)
          .onApply { clearTagTreeHighlighting() }
          .sizeGroup(spinnerGroupName)
          .enableIf(enable.selected)

      }
      row(XmlBundle.message("settings.opacity")) {
        component(JSpinner())
          .applyToComponent { model = SpinnerNumberModel(0.0, 0.0, 1.0, 0.05) }
          .withBinding({ ((it.value as Double) * 100).toInt() }, { it, value -> it.value = value * 0.01 },
                       PropertyBinding(options::getTagTreeHighlightingOpacity, options::setTagTreeHighlightingOpacity))
          .onApply { clearTagTreeHighlighting() }
          .sizeGroup(spinnerGroupName)
          .enableIf(enable.selected)
        largeGapAfter()
      }
    }
  }

  companion object {
    private fun clearTagTreeHighlighting() {
      for (project in ProjectManager.getInstance().openProjects) {
        for (fileEditor in FileEditorManager.getInstance(project).allEditors) {
          if (fileEditor is TextEditor) {
            val editor = fileEditor.editor
            XmlTagTreeHighlightingPass.clearHighlightingAndLineMarkers(editor, project)
            val breadcrumbs = BreadcrumbsPanel.getBreadcrumbsComponent(editor)
            breadcrumbs?.queueUpdate()
          }
        }
      }
    }
  }
}
