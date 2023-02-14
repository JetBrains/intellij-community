// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.xml;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlFileIndentOptionsProvider extends FileIndentOptionsProvider {
  @Override
  public @Nullable IndentOptions getIndentOptions(@NotNull Project project, @NotNull CodeStyleSettings settings, @NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage().isKindOf(HTMLLanguage.INSTANCE)) {
      HtmlCodeStyleSettings htmlSettings = settings.getCustomSettings(HtmlCodeStyleSettings.class);
      Language language = ((LanguageFileType)fileType).getLanguage();
      IndentOptions options = settings.getLanguageIndentOptions(language);
      if (htmlSettings.HTML_UNIFORM_INDENT && options != null) {
        options = (IndentOptions)options.clone();
        options.setOverrideLanguageOptions(true);
        return options;
      }
    }
    return null;
  }
}
