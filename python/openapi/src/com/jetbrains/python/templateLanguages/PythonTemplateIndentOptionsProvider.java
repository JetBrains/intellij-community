/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.templateLanguages;

import com.intellij.lang.Language;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import com.intellij.psi.codeStyle.FileIndentOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Injects indents for Python templates.
 * In Python we have template langs, but we should use indent from underlying language (like html)
 * because templ. language never works standalone: it is always emebedded in some language
 *
 * @author Ilya.Kazakevich
 */
public class PythonTemplateIndentOptionsProvider extends FileIndentOptionsProvider {
  @Nullable
  @Override
  public final IndentOptions getIndentOptions(@NotNull final CodeStyleSettings settings,
                                              @NotNull final PsiFile file) {
    final Language language = file.getLanguage();
    if (!(language instanceof PythonTemplateLanguage)) {
      return null; // We only care about python template files
    }

    // This template language has no settings, lets use parent language then
    final Language templateDataLanguage = PyTemplatesUtil.getTemplateDataLanguage(file, null);
    if (templateDataLanguage == null) {
      return null; // No template data language
    }
    return settings.getIndentOptions(templateDataLanguage.getAssociatedFileType());
  }
}
