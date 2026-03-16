// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.CodeFoldingOptionsProvider
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.options.ConfigurableWithOptionDescriptors
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.ui.dsl.builder.Panel
import java.util.function.Function

private val longStringLiterals: CheckboxDescriptor
  get() = CheckboxDescriptor(PyBundle.message("python.long.string.literals"), PythonFoldingSettings.getInstance()::COLLAPSE_LONG_STRINGS)
private val longCollectionLiterals: CheckboxDescriptor
  get() = CheckboxDescriptor(PyBundle.message("python.long.collection.literals"), PythonFoldingSettings.getInstance()::COLLAPSE_LONG_COLLECTIONS)
private val sequentialComments: CheckboxDescriptor
  get() = CheckboxDescriptor(PyBundle.message("python.sequential.comments"), PythonFoldingSettings.getInstance()::COLLAPSE_SEQUENTIAL_COMMENTS)
private val typeAnnotations: CheckboxDescriptor
  get() = CheckboxDescriptor(PyBundle.message("python.type.annotations"), PythonFoldingSettings.getInstance()::COLLAPSE_TYPE_ANNOTATIONS,
                             comment = PyBundle.message("python.type.annotations.hint"))

class PythonFoldingOptionsProvider : UiDslUnnamedConfigurable.Simple(), CodeFoldingOptionsProvider, ConfigurableWithOptionDescriptors {

  override fun Panel.createContent() {
    group(PyBundle.message("python.folding.options.title")) {
      row {
        checkBox(longStringLiterals)
      }
      row {
        checkBox(longCollectionLiterals)
      }
      row {
        checkBox(sequentialComments)
      }
      row {
        checkBox(typeAnnotations)
      }
    }
  }

  override fun getOptionDescriptors(configurableId: String, nameConverter: Function<in String?, String?>): List<OptionDescription> {
    return listOf(longStringLiterals, longCollectionLiterals, sequentialComments, typeAnnotations).map { it.asOptionDescriptor() }
  }
}
