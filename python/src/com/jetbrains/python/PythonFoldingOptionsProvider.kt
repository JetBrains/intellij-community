// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.openapi.options.BeanConfigurable
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected

class PythonFoldingOptionsProvider : BeanConfigurable<PythonFoldingSettings>(
  PythonFoldingSettings.getInstance(), PyBundle.message("python.folding.options.title")),
                                     CodeFoldingOptionsProvider {

  override fun Panel.createContent() {
    group(PyBundle.message("python.folding.options.title")) {
      row {
        checkBox(PyBundle.message("python.long.string.literals"))
          .bindSelected(instance::isCollapseLongStrings) { v: Boolean -> instance.COLLAPSE_LONG_STRINGS = v }
      }
      row {
        checkBox(PyBundle.message("python.long.collection.literals"))
          .bindSelected(instance::isCollapseLongCollections) { v: Boolean -> instance.COLLAPSE_LONG_COLLECTIONS = v }
      }
      row {
        checkBox(PyBundle.message("python.sequential.comments"))
          .bindSelected(instance::isCollapseSequentialComments) { v: Boolean -> instance.COLLAPSE_SEQUENTIAL_COMMENTS = v }
      }
      row {
        checkBox(PyBundle.message("python.type.annotations"))
          .bindSelected(instance::isCollapseTypeAnnotations) { v: Boolean -> instance.COLLAPSE_TYPE_ANNOTATIONS = v }
          .comment(PyBundle.message("python.type.annotations.hint"))
      }
    }
  }
}