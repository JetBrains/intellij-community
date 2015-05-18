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
package com.jetbrains.python.codeInsight;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyTypingAnnotationInjector extends PyInjectorBase {
  public static final Pattern RE_TYPING_ANNOTATION = Pattern.compile("\\s*\\S+(\\[.*\\])?\\s*");

  @Nullable
  @Override
  public Language getInjectedLanguage(@NotNull PsiElement context) {
    if (context instanceof PyStringLiteralExpression) {
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)context;
      if (PsiTreeUtil.getParentOfType(context, PyAnnotation.class, true) != null && isTypingAnnotation(expr.getStringValue())) {
        return PyDocstringLanguageDialect.getInstance();
      }
    }
    return null;
  }

  private static boolean isTypingAnnotation(@NotNull String s) {
    return RE_TYPING_ANNOTATION.matcher(s).matches();
  }
}
