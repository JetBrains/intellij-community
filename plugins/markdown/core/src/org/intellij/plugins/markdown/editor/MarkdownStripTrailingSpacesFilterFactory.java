// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.StripTrailingSpacesFilter;
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory;
import com.intellij.openapi.editor.impl.PsiBasedStripTrailingSpacesFilter;
import com.intellij.openapi.project.Project;
import org.intellij.plugins.markdown.lang.MarkdownLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkdownStripTrailingSpacesFilterFactory extends StripTrailingSpacesFilterFactory {

  @Override
  public @NotNull StripTrailingSpacesFilter createFilter(@Nullable Project project, @NotNull Document document) {
    Language documentLanguage = PsiBasedStripTrailingSpacesFilter.getDocumentLanguage(document);
    if (documentLanguage != null && documentLanguage.is(MarkdownLanguage.INSTANCE)) {
      return StripTrailingSpacesFilter.NOT_ALLOWED;
    }
    return StripTrailingSpacesFilter.ALL_LINES;
  }
}
