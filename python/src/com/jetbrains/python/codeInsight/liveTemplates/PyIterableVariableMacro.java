// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.liveTemplates;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.PsiElementResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyImplicitImportNameDefiner;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public final class PyIterableVariableMacro extends Macro {
  @Override
  public String getName() {
    return "pyIterableVariable";
  }

  @Override
  public @Nullable Result calculateResult(Expression @NotNull [] params, @NotNull ExpressionContext context) {
    final PsiElement element = context.getPsiElementAtStartOffset();
    if (element != null) {
      final List<PsiNamedElement> components = getIterableElements(element);
      if (!components.isEmpty()) {
        if (components.get(0) instanceof PyNamedParameter namedParameter) {
          return new PsiElementResult(namedParameter.getNameIdentifier());
        }
        return new PsiElementResult(components.get(0));
      }
    }
    return null;
  }

  @Override
  public LookupElement @Nullable [] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    final List<LookupElement> results = new ArrayList<>();
    final PsiElement element = context.getPsiElementAtStartOffset();
    if (element != null) {
      for (PsiNamedElement iterableElement : getIterableElements(element)) {
        final String name = iterableElement.getName();
        if (name != null) {
          results.add(LookupElementBuilder.createWithSmartPointer(name, iterableElement));
        }
      }
    }
    return results.toArray(LookupElement.EMPTY_ARRAY);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof PythonTemplateContextType;
  }

  private static @NotNull List<PsiNamedElement> getIterableElements(@NotNull PsiElement element) {
    final TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    final List<PsiNamedElement> components = new ArrayList<>();
    for (PsiNamedElement namedElement : getVisibleNamedElements(element)) {
      if (namedElement instanceof PyTypedElement) {
        final PyType type = typeEvalContext.getType((PyTypedElement)namedElement);
        if (type != null && PyABCUtil.isSubtype(type, PyNames.ITERABLE, typeEvalContext)) {
          components.add(namedElement);
        }
      }
    }
    return components;
  }

  private static @NotNull List<PsiNamedElement> getVisibleNamedElements(@NotNull PsiElement anchor) {
    final List<PsiNamedElement> results = new ArrayList<>();
    for (ScopeOwner owner = ScopeUtil.getScopeOwner(anchor); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
      final Scope scope = ControlFlowCache.getScope(owner);
      results.addAll(scope.getNamedElements());

      StreamEx
        .of(scope.getImportedNameDefiners())
        .filter(definer -> !(definer instanceof PyImplicitImportNameDefiner))
        .flatMap(definer -> StreamSupport.stream(definer.iterateNames().spliterator(), false))
        .select(PsiNamedElement.class)
        .forEach(results::add);
    }
    return results;
  }
}
