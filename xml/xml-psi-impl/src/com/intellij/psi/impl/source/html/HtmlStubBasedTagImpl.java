// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.xml.XmlStubBasedTag;
import com.intellij.psi.impl.source.xml.XmlTagDelegate;
import com.intellij.psi.impl.source.xml.stub.XmlTagStubImpl;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HtmlStubBasedTagImpl extends XmlStubBasedTag implements HtmlTag {

  public HtmlStubBasedTagImpl(@NotNull XmlTagStubImpl stub,
                              @NotNull IStubElementType<? extends XmlTagStubImpl, ? extends HtmlStubBasedTagImpl> nodeType) {
    super(stub, nodeType);
  }

  public HtmlStubBasedTagImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public boolean isCaseSensitive() {
    return false;
  }

  @Override
  public @Nullable String getRealNs(final @Nullable String value) {
    if (XmlUtil.XHTML_URI.equals(value)) return XmlUtil.HTML_URI;
    return value;
  }

  @Override
  public String toString() {
    return "HtmlTag:" + getName();
  }

  @Override
  public XmlTag getParentTag() {
    return PsiTreeUtil.getParentOfType(this, XmlTag.class);
  }

  @Override
  protected @NotNull XmlTagDelegate createDelegate() {
    return new HtmlStubBasedTagImplDelegate();
  }

  protected class HtmlStubBasedTagImplDelegate extends HtmlTagDelegate {

    public HtmlStubBasedTagImplDelegate() {
      super(HtmlStubBasedTagImpl.this);
    }

    @Override
    protected void deleteChildInternalSuper(@NotNull ASTNode child) {
      HtmlStubBasedTagImpl.this.deleteChildInternalSuper(child);
    }

    @Override
    protected TreeElement addInternalSuper(TreeElement first, ASTNode last, @Nullable ASTNode anchor, @Nullable Boolean before) {
      return HtmlStubBasedTagImpl.this.addInternalSuper(first, last, anchor, before);
    }
  }

}
