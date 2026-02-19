// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CDataToTextIntention implements IntentionAction {
  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
    return getFamilyName();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
    return XmlBundle.message("convert.cdata.to.text");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return getCData(editor, psiFile) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    PsiElement cdata = getCData(editor, psiFile);
    if (cdata == null) return;

    ASTNode node = cdata.getNode();
    List<ASTNode> cdatas = new ArrayList<>();
    ASTNode curr = node.getTreePrev();
    while (curr != null && curr.getElementType() == XmlElementType.XML_CDATA) {
      cdatas.add(0, curr);
      curr = curr.getTreePrev();
    }
    cdatas.add(node);
    curr = node.getTreeNext();
    while (curr != null && curr.getElementType() == XmlElementType.XML_CDATA) {
      cdatas.add(curr);
      curr = curr.getTreeNext();
    }
    StringBuilder text = new StringBuilder();
    for (ASTNode astNode : cdatas) {
      ASTNode textNode = astNode.getFirstChildNode().getTreeNext();
      if (textNode != null && textNode.getElementType() != XmlTokenType.XML_CDATA_END) text.append(StringUtil.escapeXmlEntities(textNode.getText()));
    }

    editor.getDocument().replaceString(cdatas.get(0).getStartOffset(),
                                       cdatas.get(cdatas.size() - 1).getTextRange().getEndOffset(), text.toString());
  }

  private static PsiElement getCData(Editor editor, PsiFile psiFile) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = psiFile.findElementAt(offset);
    PsiElement parent = element != null ? element.getParent() : null;
    if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == XmlElementType.XML_CDATA) return parent;

    element = psiFile.findElementAt(offset - 1);
    parent = element != null ? element.getParent() : null;
    if (parent != null && parent.getNode() != null && parent.getNode().getElementType() == XmlElementType.XML_CDATA) return parent;
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
