/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.lookup.LookupValueWithPsiElement;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.codeInsight.lookup.PresentableLookupValue;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationManagerImpl extends ElementPresentationManager {

  @NotNull
  public <T> Object[] createVariants(Collection<T> elements, Function<T, String> namer, int iconFlags) {
    ArrayList<Object> result = new ArrayList<Object>(elements.size());
    for (T element: elements) {
      String name = namer.fun(element);
      if (name != null) {
        Object value = createVariant(element, name, null);
        result.add(value);
      }
    }
    return result.toArray();
  }

  public Object createVariant(final Object variant, final String name, final PsiElement psiElement) {
    return new DomVariant(variant, name, psiElement);
  }

  private static class DomVariant implements PresentableLookupValue, Iconable, LookupValueWithUIHint, LookupValueWithPsiElement {

    private final Object myVariant;
    private final String myName;
    private final PsiElement myElement;

    public DomVariant(final Object variant, String name, final PsiElement element) {
      myVariant = variant;
      myName = name;
      myElement = element;
    }

    public String getPresentation() {
      return myName;
    }

    @Nullable
    public Icon getIcon(final int flags) {
      return ElementPresentationManager.getIcon(myVariant);
    }

    @Nullable
    public String getTypeHint() {
      return null;
    }

    @Nullable
    public Color getColorHint() {
      return null;
    }

    public boolean isBold() {
      return false;
    }

    @Nullable
    public PsiElement getElement() {
      return myElement;
    }
  }
}
