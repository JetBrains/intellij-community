/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

package com.intellij.psi;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.resolve.reference.ElementManipulator;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceBase<T extends PsiElement> implements PsiReference {

  protected final T myElement;
  private TextRange myRange;
  protected boolean mySoft;

  public PsiReferenceBase(T element, TextRange range, boolean soft) {
    this(element, range);
    mySoft = soft;
  }

  public PsiReferenceBase(T element, TextRange range) {
    this(element);
    myRange = range;
  }

  public PsiReferenceBase(T element, boolean soft) {
    this(element);
    mySoft = soft;
  }

  public PsiReferenceBase(T element) {
    myElement = element;
  }

  public void setRangeInElement(TextRange range) {
    myRange = range;
  }

  public String getValue() {
    String text = myElement.getText();
    return getRangeInElement().substring(text);
  }


  public T getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    if (myRange == null) {
      final ElementManipulator<T> manipulator = ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement);
      assert manipulator != null: "Cannot find manipulator for " + myElement;
      myRange = manipulator.getRangeInElement(myElement);
    }
    return myRange;
  }

  public String getCanonicalText() {
    return getValue();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<T> manipulator = ReferenceProvidersRegistry.getInstance(myElement.getProject()).getManipulator(myElement);
    assert manipulator != null: "Cannot find manipulator for " + myElement;
    return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
  }                                                              

  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Rebind cannot be performed");
  }

  public boolean isReferenceTo(PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public static <T extends PsiElement> PsiReferenceBase<T> createSelfReference(T element, final PsiElement resolveTo) {

    return new PsiReferenceBase<T>(element) {

      @Nullable
      public PsiElement resolve() {
        return resolveTo;
      }

      public Object[] getVariants() {
        return EMPTY_ARRAY;
      }
    };
  }

  @Nullable
  public Module getModule() {
    return ModuleUtil.findModuleForPsiElement(myElement);
  }

  public boolean isSoft() {
    return mySoft;
  }
}
