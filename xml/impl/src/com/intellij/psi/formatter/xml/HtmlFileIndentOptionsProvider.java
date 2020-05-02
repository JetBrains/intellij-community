// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.xml;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import com.intellij.psi.impl.source.html.HtmlFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlFileIndentOptionsProvider extends FileIndentOptionsProvider {
  @Override
  public @Nullable IndentOptions getIndentOptions(@NotNull CodeStyleSettings settings, @NotNull PsiFile file) {
    if (file instanceof HtmlFileImpl) {
      HtmlCodeStyleSettings htmlSettings = settings.getCustomSettings(HtmlCodeStyleSettings.class);
      IndentOptions options = settings.getLanguageIndentOptions(file.getLanguage());
      if (htmlSettings.HTML_UNIFORM_INDENT && options != null) {
        options = (IndentOptions)options.clone();
        options.setOverrideLanguageOptions(true);
        return options;
      }
    }
    return null;
  }
}
