// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiInvalidElementAccessException;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public abstract class VariantsProcessor implements PsiScopeProcessor {

  protected final PsiElement myContext;

  @Nullable
  protected final Condition<PsiElement> myNodeFilter;

  @Nullable
  protected final Condition<String> myNameFilter;

  protected final boolean myPlainNamesOnly; // if true, add insert handlers to known things like functions

  @Nullable
  private Set<String> myAllowedNames;

  @NotNull
  private final Set<String> mySeenNames = new HashSet<>();

  public VariantsProcessor(PsiElement context) {
    this(context, null, null, false);
  }

  public VariantsProcessor(PsiElement context, @Nullable Condition<PsiElement> nodeFilter, @Nullable Condition<String> nameFilter) {
    this(context, nodeFilter, nameFilter, false);
  }

  public VariantsProcessor(PsiElement context,
                           @Nullable Condition<PsiElement> nodeFilter,
                           @Nullable Condition<String> nameFilter,
                           boolean plainNamesOnly) {
    myContext = context;
    myNodeFilter = nodeFilter;
    myNameFilter = nameFilter;
    myPlainNamesOnly = plainNamesOnly;
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState substitutor) {
    if (myNodeFilter != null && !myNodeFilter.value(element)) return true; // skip whatever the filter rejects
    if (element instanceof PsiNamedElement namedElement) {
      final String name = PyUtil.getElementNameWithoutExtension(namedElement);
      if (nameIsAcceptable(name)) {
        addElement(name, namedElement);
      }
    }
    else if (element instanceof PyReferenceExpression referenceExpression) {
      final String name = referenceExpression.getReferencedName();
      if (nameIsAcceptable(name)) {
        addElement(name, referenceExpression);
      }
    }
    else if (element instanceof PyImportedNameDefiner) {
      if (!(element instanceof PyImportElement) || !handleImportElement((PyImportElement)element)) {
        final PyImportedNameDefiner definer = (PyImportedNameDefiner)element;
        for (PyElement expr : definer.iterateNames()) {
          if (expr != null && expr != myContext) { // NOTE: maybe rather have SingleIterables skip nulls outright?
            if (!expr.isValid()) {
              throw new PsiInvalidElementAccessException(expr, "Definer: " + definer);
            }
            final String name = expr instanceof PyFile ? FileUtilRt.getNameWithoutExtension(((PyFile)expr).getName()) : expr.getName();
            if (nameIsAcceptable(name)) {
              addImportedElement(name, expr);
            }
          }
        }
      }
    }

    return true;
  }

  private boolean handleImportElement(@NotNull PyImportElement importElement) {
    final QualifiedName qName = importElement.getImportedQName();
    if (qName != null && qName.getComponentCount() == 1) {
      final String name = importElement.getAsName() != null ? importElement.getAsName() : qName.getLastComponent();
      if (nameIsAcceptable(name)) {
        final PsiElement resolved = importElement.resolve();
        if (resolved instanceof PsiNamedElement) {
          addElement(name, resolved);
          return true;
        }
      }
    }
    return false;
  }

  protected abstract void addElement(@NotNull String name, @NotNull PsiElement element);

  protected void addImportedElement(@NotNull String name, @NotNull PyElement element) {
    addElement(name, element);
  }

  protected void markAsProcessed(@NotNull String name) {
    mySeenNames.add(name);
  }

  @Contract("null -> false")
  private boolean nameIsAcceptable(@Nullable String name) {
    if (name == null) {
      return false;
    }
    if (mySeenNames.contains(name)) {
      return false;
    }
    if (myNameFilter != null && !myNameFilter.value(name)) {
      return false;
    }
    if (myAllowedNames != null && !myAllowedNames.contains(name)) {
      return false;
    }
    return true;
  }

  public void setAllowedNames(@Nullable Collection<String> allowedNames) {
    if (allowedNames == null) {
      myAllowedNames = null;
    }
    else {
      myAllowedNames = new HashSet<>(allowedNames);
    }
  }
}
