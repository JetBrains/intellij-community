// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


public class PyJavaMethodType implements PyCallableType {
  private final PsiMethod myMethod;

  public PyJavaMethodType(PsiMethod method) {
    myMethod = method;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    return PyJavaTypeProvider.asPyType(myMethod.getReturnType());
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return getReturnType(context);
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return Collections.emptyList();
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  @Override
  public String getName() {
    final PsiClass cls = myMethod.getContainingClass();
    return "Java method(" + (cls != null ? cls.getQualifiedName() : null) + "." + myMethod.getName() + ")";
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}
