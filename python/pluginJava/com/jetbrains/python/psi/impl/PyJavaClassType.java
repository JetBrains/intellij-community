// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class PyJavaClassType implements PyClassLikeType {
  private final PsiClass myClass;
  private final boolean myDefinition;

  public PyJavaClassType(final PsiClass aClass, boolean definition) {
    myClass = aClass;
    myDefinition = definition;
  }

  @Override
  @Nullable
  public List<? extends RatedResolveResult> resolveMember(@NotNull final String name,
                                                          @Nullable PyExpression location,
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

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(location);
    myClass.processDeclarations(processor, ResolveState.initial(), null, location);
    return processor.getResult();
  }

  @Override
  @Nullable
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

  @Override
  public boolean isDefinition() {
    return myDefinition;
  }

  @NotNull
  @Override
  public PyClassLikeType toInstance() {
    return myDefinition ? new PyJavaClassType(myClass, false) : this;
  }

  @NotNull
  @Override
  public PyClassLikeType toClass() {
    return myDefinition ? this : new PyJavaClassType(myClass, true);
  }

  @Nullable
  @Override
  public String getClassQName() {
    return myClass.getQualifiedName();
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getSuperClassTypes(@NotNull TypeEvalContext context) {
    final List<PyClassLikeType> result = new ArrayList<>();
    for (PsiClass cls : myClass.getSupers()) {
      result.add(new PyJavaClassType(cls, myDefinition));
    }
    return result;
  }

  @Override
  public void visitMembers(final @NotNull Processor<? super PsiElement> processor, final boolean inherited, @NotNull TypeEvalContext context) {
    for (PsiMethod method : myClass.getAllMethods()) {
      processor.process(method);
    }

    for (PsiField field : myClass.getAllFields()) {
      processor.process(field);
    }

    if (!inherited) {
      return;
    }

    for (PyClassLikeType type : getAncestorTypes(context)) {
      if (type != null) {
        type.visitMembers(processor, false, context);
      }
    }
  }

  @NotNull
  @Override
  public Set<String> getMemberNames(boolean inherited, @NotNull TypeEvalContext context) {
    final Set<String> result = new LinkedHashSet<>();

    for (PsiMethod method : myClass.getAllMethods()) {
      result.add(method.getName());
    }

    for (PsiField field : myClass.getAllFields()) {
      result.add(field.getName());
    }

    if (inherited) {
      for (PyClassLikeType type : getAncestorTypes(context)) {
        if (type != null) {
          result.addAll(type.getMemberNames(false, context));
        }
      }
    }

    return result;
  }

  @NotNull
  @Override
  public List<PyClassLikeType> getAncestorTypes(@NotNull final TypeEvalContext context) {
    final List<PyClassLikeType> result = new ArrayList<>();

    final Deque<PsiClass> deque = new LinkedList<>();
    final Set<PsiClass> visited = new HashSet<>();

    deque.addAll(Arrays.asList(myClass.getSupers()));

    while (!deque.isEmpty()) {
      final PsiClass current = deque.pollFirst();

      if (current == null || !visited.add(current)) {
        continue;
      }

      result.add(new PyJavaClassType(current, myDefinition));

      deque.addAll(Arrays.asList(current.getSupers()));
    }

    return result;
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
    if (!(o instanceof PyJavaClassType type)) return false;

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
