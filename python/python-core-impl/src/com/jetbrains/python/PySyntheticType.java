// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PySyntheticType implements PyType {
  private final List<PyCustomMember> myMembers;
  private final String myName;
  private final PsiElement myContext;

  public PySyntheticType(String name, PsiElement context, PyCustomMember... members) {
    myName = name;
    myContext = context;
    myMembers = Lists.newArrayList(members);
  }

  @Override
  public List<? extends RatedResolveResult> resolveMember(@NotNull String name,
                                                          @Nullable PyExpression location,
                                                          @NotNull AccessDirection direction,
                                                          @NotNull PyResolveContext resolveContext) {
    final ResolveResultList result = new ResolveResultList();
    for (PyCustomMember member : myMembers) {
      if (name.equals(member.getName())) {
        result.poke(member.resolve(myContext, resolveContext), RatedResolveResult.RATE_NORMAL);
      }
    }
    return result;
  }

  @Override
  public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
    final List<Object> result = new ArrayList<>();
    for (PyCustomMember member : myMembers) {
      result.add(LookupElementBuilder.create(member.getName()));
    }
    return result.toArray();
  }

  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }
}
