// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;


public final class PyConstructorArgumentCompletionContributor extends CompletionContributor implements DumbAware {
  public PyConstructorArgumentCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement()
             .withParents(PyReferenceExpression.class, PyArgumentList.class, PyCallExpression.class),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           @NotNull ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               final PyCallExpression call = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), PyCallExpression.class);
               if (call == null) return;
               final PyExpression calleeExpression = call.getCallee();
               if (calleeExpression instanceof PyReferenceExpression) {
                 final PsiElement callee = ((PyReferenceExpression)calleeExpression).getReference().resolve();
                 if (callee instanceof PsiClass) {
                   addSettersAndListeners(result, (PsiClass)callee, parameters.getOriginalFile());
                 }
                 else if (callee instanceof PsiMethod && ((PsiMethod)callee).isConstructor()) {
                   final PsiClass containingClass = ((PsiMethod)callee).getContainingClass();
                   assert containingClass != null;
                   addSettersAndListeners(result, containingClass, parameters.getOriginalFile());
                 }
               }
             }
           });
  }

  private static void addSettersAndListeners(CompletionResultSet result, PsiClass containingClass, PsiFile origin) {
    // see PyJavaType.init() in Jython source code for matching logic
    for (PsiMethod method : containingClass.getAllMethods()) {
      final Project project = containingClass.getProject();
      if (PropertyUtilBase.isSimplePropertySetter(method)) {
        final String propName = PropertyUtilBase.getPropertyName(method);
        result.addElement(PyUtil.createNamedParameterLookup(propName, origin));
      }
      else if (method.getName().startsWith("add") && method.getName().endsWith("Listener") && PsiTypes.voidType().equals(method.getReturnType())) {
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 1) {
          final PsiType type = parameters[0].getType();
          if (type instanceof PsiClassType) {
            final PsiClass parameterClass = ((PsiClassType)type).resolve();
            if (parameterClass != null) {
              result.addElement(PyUtil.createNamedParameterLookup(StringUtil.decapitalize(StringUtil.notNullize(parameterClass.getName())), origin));
              for (PsiMethod parameterMethod : parameterClass.getMethods()) {
                result.addElement(PyUtil.createNamedParameterLookup(parameterMethod.getName(), origin));
              }
            }
          }
        }
      }
    }
  }
}
