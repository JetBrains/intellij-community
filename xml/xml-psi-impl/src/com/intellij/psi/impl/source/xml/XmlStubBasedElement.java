// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlPsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified stub-based version of {@link XmlElementImpl}
 *
 * @apiNote if you introduce a new inheritor, please check that this implementation is aligned with XmlElementImpl
 */
@ApiStatus.Experimental
@ApiStatus.Internal
public abstract class XmlStubBasedElement<T extends StubElement<?>> extends StubBasedPsiElementBase<T> implements XmlElement {

  XmlStubBasedElement(@NotNull T stub,
                      @NotNull IStubElementType nodeType) {
    super(stub, nodeType);
  }

  XmlStubBasedElement(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public boolean processElements(PsiElementProcessor processor, PsiElement place) {
    return XmlPsiUtil.processXmlElements(this, processor, false);
  }

  @Override
  public PsiElement getContext() {
    final XmlElement data = getUserData(INCLUDING_ELEMENT);
    if (data != null) return data;
    return super.getContext();
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    if (!isPhysical()) {
      final XmlElement including = getUserData(INCLUDING_ELEMENT);
      if (including != null) {
        return including;
      }
      PsiElement astParent = super.getParent();
      PsiElement parentNavigation = astParent.getNavigationElement();
      if (parentNavigation.getTextOffset() == getTextOffset()) return parentNavigation;
      return this;
    }
    return super.getNavigationElement();
  }

  @Override
  public PsiElement getParent() {
    final XmlElement data = getUserData(INCLUDING_ELEMENT);
    if (data != null) return data;
    return super.getParent();
  }

  @Override
  public @NotNull Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return GlobalSearchScope.allScope(getProject());
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {

    if (super.isEquivalentTo(another)) return true;
    PsiElement element1 = this;

    // TODO: seem to be only necessary for tag dirs equivalents checking.
    if (element1 instanceof XmlTag && another instanceof XmlTag) {
      if (!element1.isPhysical() && !another.isPhysical()) return element1.getText().equals(another.getText());
    }

    return false;
  }

  @Override
  public boolean skipValidation() {
    return XmlElementImpl.skipValidation(this);
  }


  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    putUserData(DO_NOT_VALIDATE, null);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    PsiElement psiChild = getFirstChild();
    if (psiChild == null) return PsiElement.EMPTY_ARRAY;

    List<PsiElement> result = new ArrayList<>();
    while (psiChild != null) {
      result.add(psiChild);
      psiChild = psiChild.getNextSibling();
    }
    return PsiUtilCore.toPsiElementArray(result);
  }

  @Override
  public String toString() {
    return "PsiElement" + "(" + getElementType() + ")";
  }
}
