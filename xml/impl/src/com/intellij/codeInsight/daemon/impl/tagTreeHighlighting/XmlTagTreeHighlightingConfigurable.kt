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
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.xml.XmlBundle
import com.intellij.xml.breadcrumbs.BreadcrumbsPanel

class XmlTagTreeHighlightingConfigurable : UiDslUnnamedConfigurable.Simple() {

  override fun Panel.createContent() {
    val options = WebEditorOptions.getInstance()
    lateinit var enable: Cell<JBCheckBox>
    panel {
      row {
        enable = checkBox(XmlBundle.message("settings.enable.html.xml.tag.tree.highlighting"))
          .bindSelected(options::isTagTreeHighlightingEnabled, options::setTagTreeHighlightingEnabled)
          .onApply { clearTagTreeHighlighting() }
      }
      indent {
        row(XmlBundle.message("settings.levels.to.highlight")) {
          spinner(1..50)
            .bindIntValue({ options.tagTreeHighlightingLevelCount }, { options.tagTreeHighlightingLevelCount = it })
            .onApply { clearTagTreeHighlighting() }
            .horizontalAlign(HorizontalAlign.FILL)
          cell()
        }.layout(RowLayout.PARENT_GRID)
        row(XmlBundle.message("settings.opacity")) {
          spinner(0.0..1.0, step = 0.05)
            .bind({ ((it.value as Double) * 100).toInt() }, { it, value -> it.value = value * 0.01 },
                  MutableProperty(options::getTagTreeHighlightingOpacity, options::setTagTreeHighlightingOpacity))
            .onApply { clearTagTreeHighlighting() }
            .horizontalAlign(HorizontalAlign.FILL)
          cell()
        }.layout(RowLayout.PARENT_GRID)
          .bottomGap(BottomGap.SMALL)
      }.enabledIf(enable.selected)
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
