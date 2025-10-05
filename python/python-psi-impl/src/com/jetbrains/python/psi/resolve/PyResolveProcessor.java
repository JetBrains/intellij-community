// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.ResolveResultList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PyResolveProcessor implements PsiScopeProcessor {
  private final @NotNull String myName;
  private final boolean myLocalResolve;
  private final @NotNull Map<PsiElement, PyImportedNameDefiner> myResults = Maps.newLinkedHashMap();
  private final @NotNull Map<PsiElement, PyImportedNameDefiner> myImplicitlyImportedResults = Maps.newLinkedHashMap();
  protected @Nullable ScopeOwner myOwner;
  private boolean myTypeParameterScope;

  public PyResolveProcessor(@NotNull String name) {
    this(name, false);
  }

  public PyResolveProcessor(@NotNull String name, boolean localResolve) {
    myName = name;
    myLocalResolve = localResolve;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    final PsiNamedElement namedElement = PyUtil.as(element, PsiNamedElement.class);
    if (namedElement != null && myName.equals(namedElement.getName())) {
      return tryAddResult(element, null);
    }
    final PyImportedNameDefiner importedNameDefiner = PyUtil.as(element, PyImportedNameDefiner.class);
    if (importedNameDefiner != null) {
      final List<RatedResolveResult> results = resolveInImportedNameDefiner(importedNameDefiner);
      if (!results.isEmpty()) {
        boolean cont = true;
        for (RatedResolveResult result : results) {
          final PsiElement resolved = result.getElement();
          if (resolved != null) {
            cont = tryAddResult(resolved, importedNameDefiner) && cont;
          }
        }
        return cont;
      }
      final PyImportElement importElement = PyUtil.as(element, PyImportElement.class);
      if (importElement != null) {
        final String importName = importElement.getVisibleName();
        if (myName.equals(importName)) {
          return tryAddResult(null, importElement);
        }
      }
    }
    return myOwner == null || myOwner == ScopeUtil.getScopeOwner(element);
  }

  public @NotNull Map<PsiElement, PyImportedNameDefiner> getResults() {
    return myResults.isEmpty() ? myImplicitlyImportedResults : myResults;
  }

  public @NotNull Collection<PsiElement> getElements() {
    return getResults().keySet();
  }

  public @Nullable ScopeOwner getOwner() {
    return myOwner;
  }

  public boolean isTypeParameterScope() {
    return myTypeParameterScope;
  }

  private @NotNull List<RatedResolveResult> resolveInImportedNameDefiner(@NotNull PyImportedNameDefiner definer) {
    if (myLocalResolve) {
      final PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
      if (importElement != null) {
        return ResolveResultList.to(importElement.getElementNamed(myName, false));
      }
      else {
        return Collections.emptyList();
      }
    }
    return definer.multiResolveName(myName);
  }

  protected boolean tryAddResult(@Nullable PsiElement element, @Nullable PyImportedNameDefiner definer) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(definer != null ? definer : element);
    if (myOwner == null) {
      myOwner = owner;
      myTypeParameterScope = element instanceof PyTypeParameter;
    }
    final boolean sameScope = owner == myOwner && (element instanceof PyTypeParameter) == myTypeParameterScope;
    if (sameScope) {
      addResult(element, definer);
    }
    return sameScope;
  }

  protected final void addResult(@Nullable PsiElement element, @Nullable PyImportedNameDefiner definer) {
    // XXX: In 'from foo import foo' inside __init__.py the preferred result is explicitly imported 'foo'
    if (definer instanceof PyFromImportStatement) {
      myImplicitlyImportedResults.put(element, definer);
    }
    else {
      myResults.put(element, definer);
    }
  }
}
