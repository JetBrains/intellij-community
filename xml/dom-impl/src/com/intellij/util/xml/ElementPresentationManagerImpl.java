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

package com.intellij.util.xml;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationManagerImpl extends ElementPresentationManager {

  @Override
  @NotNull
  public <T> Object[] createVariants(Collection<T> elements, Function<T, String> namer, int iconFlags) {
    List<Object> result = new ArrayList<>(elements.size());
    for (T element : elements) {
      String name = namer.fun(element);
      if (name != null) {
        Object value = createVariant(element, name, null);
        result.add(value);
      }
    }
    return result.toArray();
  }

  @Override
  public Object createVariant(final Object variant, final String name, final PsiElement psiElement) {
    final LookupElementBuilder builder;
    if (psiElement != null) {
      builder = LookupElementBuilder.create(psiElement, name);
    }
    else {
      builder = LookupElementBuilder.create(name);
    }
    return builder.withIcon(ElementPresentationManager.getIcon(variant));
  }
}
