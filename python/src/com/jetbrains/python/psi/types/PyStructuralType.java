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
package com.jetbrains.python.psi.types;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author vlan
 */
public class PyStructuralType implements PyType {
  @NotNull private final Set<String> myAttributes;
  private final boolean myInferredFromUsages;

  public PyStructuralType(@NotNull Set<String> attributes, boolean inferredFromUsages) {
    myAttributes = attributes;
    myInferredFromUsages = inferredFromUsages;
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
    final List<Object> variants = new ArrayList<>();
    for (String attribute : myAttributes) {
      if (!attribute.equals(completionPrefix)) {
        variants.add(LookupElementBuilder.create(attribute).withIcon(PlatformIcons.FIELD_ICON));
      }
    }
    return variants.toArray();
  }

  @Nullable
  @Override
  public String getName() {
    return "{" + StringUtil.join(myAttributes, ", ") + "}";
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public void assertValid(String message) {
  }

  @Override
  public String toString() {
    return "PyStructuralType(" + StringUtil.join(myAttributes, ", ") + ")";
  }

  public boolean isInferredFromUsages() {
    return myInferredFromUsages;
  }

  public Set<String> getAttributeNames() {
    return myAttributes;
  }
}
