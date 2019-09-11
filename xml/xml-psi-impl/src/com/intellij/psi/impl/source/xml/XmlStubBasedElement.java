// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.extapi.psi.StubBasedPsiElementBase;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlPsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Simplified stub-based version of {@link XmlElementImpl}
 *
 * @apiNote if you introduce a new inheritor please check that this implementation is aligned with XmlElementImpl
 */
@ApiStatus.Experimental
abstract class XmlStubBasedElement<T extends StubElement<?>> extends StubBasedPsiElementBase<T> implements XmlElement {

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
  public boolean skipValidation() {
    return XmlElementImpl.skipValidation(this);
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return getContainingFile().getLanguage();
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    putUserData(DO_NOT_VALIDATE, null);
  }

  @Override
  public String toString() {
    return "PsiElement" + "(" + getElementType() + ")";
  }
}
