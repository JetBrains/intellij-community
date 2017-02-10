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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.LightNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class PyDunderAllReference extends PsiReferenceBase<PyStringLiteralExpression> {
  public PyDunderAllReference(@NotNull PyStringLiteralExpression element) {
    super(element);
    final List<TextRange> ranges = element.getStringValueTextRanges();
    if (!ranges.isEmpty()) {
      setRangeInElement(ranges.get(0));
    }
  }

  @Override
  public PsiElement resolve() {
    final PyStringLiteralExpression element = getElement();
    final String name = element.getStringValue();
    PyFile containingFile = (PyFile) element.getContainingFile();
    return containingFile.getElementNamed(name);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final List<LookupElement> result = new ArrayList<>();
    PyFile containingFile = (PyFile) getElement().getContainingFile().getOriginalFile();
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
            result.add(LookupElementBuilder.createWithSmartPointer(name, element).withIcon(element.getIcon(0)));
          }
        }
        else if (element instanceof PyImportElement) {
          final String visibleName = ((PyImportElement)element).getVisibleName();
          if (visibleName != null && !seenNames.contains(visibleName)) {
            seenNames.add(visibleName);
            result.add(LookupElementBuilder.createWithSmartPointer(visibleName, element));
          }
        }
        return true;
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(@NotNull Event event, @Nullable Object associated) {
      }
    }, ResolveState.initial(), null, containingFile);
    return ArrayUtil.toObjectArray(result);
  }
}
