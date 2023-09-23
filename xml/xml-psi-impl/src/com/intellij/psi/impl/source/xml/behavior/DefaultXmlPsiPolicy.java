// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class DefaultXmlPsiPolicy implements XmlPsiPolicy{
  private static final Logger LOG = Logger.getInstance(DefaultXmlPsiPolicy.class);

  @Override
  public ASTNode encodeXmlTextContents(String displayText, PsiElement text) {
    if (displayText.isEmpty()) {
      return null;
    }
    CharTable charTable = SharedImplUtil.findCharTableByTree(text.getNode());
    final FileElement dummyParent = DummyHolderFactory.createHolder(text.getManager(), null, charTable).getTreeElement();
    final XmlTag rootTag =
      ((XmlFile)PsiFileFactory.getInstance(text.getProject())
        .createFileFromText("a.xml", text.getLanguage(), buildTagForText(text, displayText), false, true)).getRootTag();

    assert rootTag != null;
    final XmlTagChild[] tagChildren = rootTag.getValue().getChildren();

    final XmlTagChild child = tagChildren.length > 0 ? tagChildren[0]:null;
    LOG.assertTrue(child != null, "Child is null for tag: " + rootTag.getText());

    final TreeElement element = (TreeElement)child.getNode();
    DebugUtil.performPsiModification(getClass().getName(), () -> {
      ((TreeElement)tagChildren[tagChildren.length - 1].getNode().getTreeNext()).rawRemoveUpToLast();
      dummyParent.rawAddChildren(element);
    });
    TreeUtil.clearCaches(dummyParent);
    return element.getFirstChildNode();
  }

  protected @NotNull String buildTagForText(PsiElement text, String displayText) {
    return "<a>" + displayText + "</a>";
  }
}
