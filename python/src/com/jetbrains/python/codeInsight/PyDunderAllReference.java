// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.LightNamedElement;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyModuleType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyDunderAllReference extends PsiPolyVariantReferenceBase<PyStringLiteralExpression> {
  public PyDunderAllReference(@NotNull PyStringLiteralExpression element) {
    super(element);
    final List<TextRange> ranges = element.getStringValueTextRanges();
    if (!ranges.isEmpty()) {
      setRangeInElement(ranges.get(0));
    }
  }

  @Override
  @NotNull
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final PyStringLiteralExpression element = getElement();
    final String name = element.getStringValue();
    final PyFile file = (PyFile)element.getContainingFile();

    final List<RatedResolveResult> resolveResults = file.multiResolveName(name);

    final boolean onlyDunderAlls = StreamEx
      .of(resolveResults)
      .map(RatedResolveResult::getElement)
      .allMatch(resolvedElement -> resolvedElement instanceof PyTargetExpression &&
                                   PyNames.ALL.equals(((PyTargetExpression)resolvedElement).getName()));

    if (onlyDunderAlls) return ResolveResult.EMPTY_ARRAY;

    return ContainerUtil.toArray(resolveResults, RatedResolveResult.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final List<LookupElement> result = new ArrayList<>();

    final PyFile containingFile = (PyFile)getElement().getContainingFile().getOriginalFile();

    final List<String> dunderAll = containingFile.getDunderAll();
    final Set<String> seenNames = new HashSet<>();
    if (dunderAll != null) {
      seenNames.addAll(dunderAll);
    }

    containingFile.processDeclarations(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiNamedElement && !(element instanceof LightNamedElement)) {
          final String name = ((PsiNamedElement)element).getName();
          if (name != null && PyUtil.getInitialUnderscores(name) == 0 && !seenNames.contains(name)) {
            seenNames.add(name);
            result.add(toLookupElement(name, element, true));
          }
        }
        else if (element instanceof PyImportElement) {
          final String visibleName = ((PyImportElement)element).getVisibleName();
          if (visibleName != null && !seenNames.contains(visibleName)) {
            seenNames.add(visibleName);
            result.add(toLookupElement(visibleName, element, false));
          }
        }
        return true;
      }
    }, ResolveState.initial(), null, containingFile);

    result.addAll(PyModuleType.getSubModuleVariants(containingFile.getParent(), getElement(), seenNames));

    return ArrayUtil.toObjectArray(result);
  }

  @NotNull
  private static LookupElement toLookupElement(@NotNull String name, @NotNull PsiElement element, boolean withIcon) {
    final LookupElementBuilder builder = LookupElementBuilder.createWithSmartPointer(name, element);
    return withIcon ? builder.withIcon(element.getIcon(0)) : builder;
  }
}
