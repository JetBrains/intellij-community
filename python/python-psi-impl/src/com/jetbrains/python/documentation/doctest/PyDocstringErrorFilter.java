/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.documentation.doctest;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 *
 * Do not highlight syntax errors in doctests
 */
public final class PyDocstringErrorFilter extends HighlightErrorFilter {

  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    final PsiFile file = element.getContainingFile();
    if (file instanceof PyDocstringFile) return false;

    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
    if (manager == null) return true;
    final PsiLanguageInjectionHost host = manager.getInjectionHost(file);
    if (host instanceof PyStringLiteralExpression && DocStringUtil.getParentDefinitionDocString(host) == host) {
      return false;
    }
    return true;
  }
}
