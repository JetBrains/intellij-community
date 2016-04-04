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

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author vlan
 */
public class PyTypingAnnotationInjector extends PyInjectorBase {
  public static final Pattern RE_TYPING_ANNOTATION = Pattern.compile("\\s*\\S+(\\[.*\\])?\\s*");

  @Override
  protected PyInjectionUtil.InjectionResult registerInjection(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    final PyInjectionUtil.InjectionResult result = super.registerInjection(registrar, context);
    if (result == PyInjectionUtil.InjectionResult.EMPTY && context instanceof PsiComment && context instanceof PsiLanguageInjectionHost) {
      return registerCommentInjection(registrar, (PsiLanguageInjectionHost)context);
    }
    return result;
  }

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

  @NotNull
  private static PyInjectionUtil.InjectionResult registerCommentInjection(@NotNull MultiHostRegistrar registrar,
                                                                          @NotNull PsiLanguageInjectionHost host) {
    final String text = host.getText();
    final Matcher m = PyTypingTypeProvider.TYPE_COMMENT_PATTERN.matcher(text);
    if (m.matches()) {
      final String annotationText = m.group(1);
      if (annotationText != null && isTypingAnnotation(annotationText)) {
        final int start = m.start(1);
        final int end = m.end(1);
        if (start < end && allowInjectionInComment(host)) {
          final Language language = PyDocstringLanguageDialect.getInstance();
          registrar.startInjecting(language);
          registrar.addPlace("", "", host, TextRange.create(start, end));
          registrar.doneInjecting();
          return new PyInjectionUtil.InjectionResult(true, true);
        }
      }
    }
    return PyInjectionUtil.InjectionResult.EMPTY;
  }

  private static boolean isTypingAnnotation(@NotNull String s) {
    return RE_TYPING_ANNOTATION.matcher(s).matches();
  }

  private static boolean allowInjectionInComment(@NotNull PsiLanguageInjectionHost host) {
    // XXX: Don't inject PyDocstringLanguage during completion inside comments due to an exception related to finding ShredImpl's
    // hostElementPointer
    if (CompletionUtil.getOriginalOrSelf(host) != host) {
      return false;
    }
    if (PyFunctionTypeCommentReferenceContributor.TYPE_COMMENT_PATTERN.accepts(host)) {
      return false;
    }
    return true;
  }
}
