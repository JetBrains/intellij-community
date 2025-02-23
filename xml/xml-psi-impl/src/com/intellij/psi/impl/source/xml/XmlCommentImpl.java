// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.injected.XmlCommentLiteralEscaper;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.xml.XmlElementType.XML_COMMENT;

public class XmlCommentImpl extends XmlElementImpl implements XmlComment, PsiMetaOwner, PsiLanguageInjectionHost {
  public XmlCommentImpl() {
    super(XML_COMMENT);
  }

  @Override
  public @NotNull IElementType getTokenType() {
    return XML_COMMENT;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof XmlElementVisitor) {
      ((XmlElementVisitor)visitor).visitXmlComment(this);
    }
    else {
      visitor.visitComment(this);
    }
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public XmlTag getParentTag() {
    if (getParent() instanceof XmlTag) return (XmlTag)getParent();
    return null;
  }

  @Override
  public XmlTagChild getNextSiblingInTag() {
    if (getParent() instanceof XmlTag) {
      PsiElement sibling = getNextSibling();
      return sibling instanceof XmlTagChild ? (XmlTagChild)sibling : null;
    }
    return null;
  }

  @Override
  public XmlTagChild getPrevSiblingInTag() {
    if (getParent() instanceof XmlTag) {
      PsiElement sibling = getPrevSibling();
      return sibling instanceof XmlTagChild ? (XmlTagChild)sibling : null;
    }
    return null;
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public @Nullable PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

  @Override
  public PsiLanguageInjectionHost updateText(final @NotNull String text) {
    final PsiFile psiFile = getContainingFile();

    final XmlDocument document =
      ((XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("dummy", psiFile.getFileType(), text)).getDocument();
    assert document != null;

    final XmlComment comment = PsiTreeUtil.getChildOfType(document, XmlComment.class);

    assert comment != null;
    replaceAllChildrenToChildrenOf(comment.getNode());

    return this;
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new XmlCommentLiteralEscaper(this);
  }

  @Override
  public @NotNull String getCommentText() {
    ASTNode node = getNode().findChildByType(XmlTokenType.XML_COMMENT_CHARACTERS);
    return node == null ? "" : node.getText();
  }
}
