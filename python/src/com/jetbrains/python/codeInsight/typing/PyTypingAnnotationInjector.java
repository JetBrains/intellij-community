// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.typing;

import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.PyInjectionUtil;
import com.jetbrains.python.codeInsight.PyInjectorBase;
import com.jetbrains.python.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import com.jetbrains.python.documentation.doctest.PyDocstringLanguageDialect;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyLiteralType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Injects fragments for type annotations either in string literals (quoted annotations containing forward references) or
 * in type comments starting with <tt># type:</tt>.
 *
 * @author vlan
 */
public class PyTypingAnnotationInjector extends PyInjectorBase {
  public static final Pattern RE_TYPING_ANNOTATION = Pattern.compile("\\s*\\S+(\\[.*\\])?\\s*");

  @Override
  protected PyInjectionUtil.InjectionResult registerInjection(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
    // Handles only string literals containing quoted types
    final PyInjectionUtil.InjectionResult result = super.registerInjection(registrar, context);
    
    if (result == PyInjectionUtil.InjectionResult.EMPTY &&
        context instanceof PsiComment &&
        context instanceof PsiLanguageInjectionHost &&
        context.getContainingFile() instanceof PyFile) {
      return registerCommentInjection(registrar, (PsiLanguageInjectionHost)context);
    }
    return result;
  }

  @Nullable
  @Override
  public Language getInjectedLanguage(@NotNull PsiElement context) {
    if (context instanceof PyStringLiteralExpression) {
      final PyStringLiteralExpression expr = (PyStringLiteralExpression)context;
      if (isTypingLiteralArgument(expr)) {
        return null;
      }
      if (PsiTreeUtil.getParentOfType(context, PyAnnotation.class, true, PyCallExpression.class) != null &&
          isTypingAnnotation(expr.getStringValue())) {
        return PyDocstringLanguageDialect.getInstance();
      }
    }
    return null;
  }

  @NotNull
  private static PyInjectionUtil.InjectionResult registerCommentInjection(@NotNull MultiHostRegistrar registrar,
                                                                          @NotNull PsiLanguageInjectionHost host) {
    final String text = host.getText();
    final String annotationText = PyTypingTypeProvider.getTypeCommentValue(text);
    if (annotationText != null) {
      final Language language;
      if (PyTypingTypeProvider.IGNORE.equals(annotationText)) {
        language = null;
      }
      else if (isFunctionTypeComment(host)) {
        language = PyFunctionTypeAnnotationDialect.INSTANCE;
      }
      else {
        language = PyDocstringLanguageDialect.getInstance();
      }
      if (language != null) {
        registrar.startInjecting(language);
        //noinspection ConstantConditions
        registrar.addPlace("", "", host, PyTypingTypeProvider.getTypeCommentValueRange(text));
        registrar.doneInjecting();
        return new PyInjectionUtil.InjectionResult(true, true);
      }
    }
    return PyInjectionUtil.InjectionResult.EMPTY;
  }

  private static boolean isTypingLiteralArgument(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PyTupleExpression) parent = parent.getParent();
    if (!(parent instanceof PySubscriptionExpression)) return false;

    final TypeEvalContext context = TypeEvalContext.codeAnalysis(element.getProject(), element.getContainingFile());
    return Ref.deref(PyTypingTypeProvider.getType((PySubscriptionExpression)parent, context)) instanceof PyLiteralType;
  }

  private static boolean isFunctionTypeComment(@NotNull PsiElement comment) {
   final PyFunction function = PsiTreeUtil.getParentOfType(comment, PyFunction.class);
    return function != null && function.getTypeComment() == comment;
  }

  private static boolean isTypingAnnotation(@NotNull String s) {
    return RE_TYPING_ANNOTATION.matcher(s).matches();
  }
}
