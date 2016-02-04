/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Maps;
import com.intellij.openapi.util.Key;
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

/**
 * @author vlan
 */
public class PyResolveProcessor implements PsiScopeProcessor {
  @NotNull private final String myName;
  private final boolean myLocalResolve;
  @NotNull private final Map<PsiElement, PyImportedNameDefiner> myResults = Maps.newLinkedHashMap();
  @NotNull private final Map<PsiElement, PyImportedNameDefiner> myImplicitlyImportedResults = Maps.newLinkedHashMap();
  @Nullable private ScopeOwner myOwner;

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

  @Nullable
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    return null;
  }

  @Override
  public void handleEvent(@NotNull Event event, @Nullable Object associated) {
  }

  @NotNull
  public Map<PsiElement, PyImportedNameDefiner> getResults() {
    return myResults.isEmpty() ? myImplicitlyImportedResults : myResults;
  }

  @NotNull
  public Collection<PsiElement> getElements() {
    return getResults().keySet();
  }

  @Nullable
  public ScopeOwner getOwner() {
    return myOwner;
  }

  @NotNull
  private List<RatedResolveResult> resolveInImportedNameDefiner(@NotNull PyImportedNameDefiner definer) {
    if (myLocalResolve) {
      final PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
      if (importElement != null) {
        return ResolveResultList.to(importElement.getElementNamed(myName, false));
      }
      else if (definer instanceof PyStarImportElement) {
        return Collections.emptyList();
      }
    }
    return definer.multiResolveName(myName);
  }

  private boolean tryAddResult(@Nullable PsiElement element, @Nullable PyImportedNameDefiner definer) {
    final ScopeOwner owner = ScopeUtil.getScopeOwner(definer != null ? definer : element);
    if (myOwner == null) {
      myOwner = owner;
    }
    final boolean sameScope = owner == myOwner;
    if (sameScope) {
      // XXX: In 'from foo import foo' inside __init__.py the preferred result is explicitly imported 'foo'
      if (definer instanceof PyFromImportStatement) {
        myImplicitlyImportedResults.put(element, definer);
      }
      else {
        myResults.put(element, definer);
      }
    }
    return sameScope;
  }
}
