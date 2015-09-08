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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyJavaClassType implements PyClassLikeType {
  private final PsiClass myClass;
  private final boolean myDefinition;

  public PyJavaClassType(final PsiClass aClass, boolean definition) {
    myClass = aClass;
    myDefinition = definition;
  }

  @Nullable
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    return resolveMember(name, location, direction, resolveContext, true);
  }

  @Nullable
  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext,
                                                          boolean inherited) {
    final PsiMethod[] methods = myClass.findMethodsByName(name, inherited);
    if (methods.length > 0) {
      ResolveResultList resultList = new ResolveResultList();
      for (PsiMethod method : methods) {
        resultList.poke(method, RatedResolveResult.RATE_NORMAL);
      }
      return resultList;
    }
    final PsiField field = myClass.findFieldByName(name, inherited);
    if (field != null) return ResolveResultList.to(field);
    return null;
  }

  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location);
    myClass.processDeclarations(processor, ResolveState.initial(), null, location);
    return processor.getResult();
  }

  public String getName() {
    if (myClass != null) {
      return myClass.getName();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isBuiltin() {
    return false;  // TODO: JDK's types could be considered built-in.
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public boolean isCallable() {
    return myDefinition;
  }

  @Nullable
  @Override
  public PyType getReturnType(@NotNull TypeEvalContext context) {
    if (myDefinition) {
      return new PyJavaClassType(myClass, false);
    }
    return null;
  }

  @Nullable
  @Override
  public PyType getCallType(@NotNull TypeEvalContext context, @NotNull PyCallSiteExpression callSite) {
    return getReturnType(context);
  }

  @Nullable
  @Override
  public List<PyCallableParameter> getParameters(@NotNull TypeEvalContext context) {
    return null;
  }

  @Override
  public boolean isDefinition() {
    return myDefinition;
  }

  @Override
  public PyClassLikeType toInstance() {
    return myDefinition ? new PyJavaClassType(myClass, false) : this;
  }

  @Nullable
  @Override
  public String getClassQName() {
    return myClass.getQualifiedName();
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> result = new ArrayList<PyClassLikeType>();
    for (PsiClass cls : myClass.getSupers()) {
      result.add(new PyJavaClassType(cls, myDefinition));
    }
    return result;
  }

  @Override
  public void visitMembers(@NotNull final Processor<PsiElement> processor, final boolean inherited, @NotNull TypeEvalContext context) {
    // TODO: Implement
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getAncestorTypes(@NotNull final TypeEvalContext context) {
    // TODO: Implement
    return Collections.emptyList();
  }

  @Override
  public boolean isValid() {
    return myClass.isValid();
  }

  @Nullable
  @Override
  public PyClassLikeType getMetaClassType(@NotNull TypeEvalContext context, boolean inherited) {
    return null;
  }

  public PsiClass getPsiClass() {
    return myClass;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PyJavaClassType)) return false;

    PyJavaClassType type = (PyJavaClassType)o;

    if (myDefinition != type.myDefinition) return false;
    if (myClass != null ? !myClass.equals(type.myClass) : type.myClass != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myClass != null ? myClass.hashCode() : 0;
    result = 31 * result + (myDefinition ? 1 : 0);
    return result;
  }
}
