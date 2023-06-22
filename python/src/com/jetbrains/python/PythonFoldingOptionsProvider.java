// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.openapi.options.BeanConfigurable;

import java.util.Objects;

final class PythonFoldingOptionsProvider extends BeanConfigurable<PythonFoldingSettings> implements CodeFoldingOptionsProvider {
  private PythonFoldingOptionsProvider() {
    super(PythonFoldingSettings.getInstance(), PyBundle.message("python.folding.options.title"));

    PythonFoldingSettings settings = Objects.requireNonNull(getInstance());
    checkBox(PyBundle.message("python.long.string.literals"), settings::isCollapseLongStrings, v -> settings.COLLAPSE_LONG_STRINGS = v);
    checkBox(PyBundle.message("python.long.collection.literals"), settings::isCollapseLongCollections, v -> settings.COLLAPSE_LONG_COLLECTIONS = v);
    checkBox(PyBundle.message("python.sequential.comments"), settings::isCollapseSequentialComments, v -> settings.COLLAPSE_SEQUENTIAL_COMMENTS = v);
    checkBox(PyBundle.message("python.type.annotations"), settings::isCollapseTypeAnnotations, v -> settings.COLLAPSE_TYPE_ANNOTATIONS = v);
  }
}
