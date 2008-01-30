package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.UnwrapDescriptor;
import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;

import java.util.Collections;
import java.util.List;

public class XmlUnwrapDescriptor implements UnwrapDescriptor {
  // todo     if (!CodeInsightUtil.prepareFileForWrite(tag.getContainingFile())) return;
  // todo       PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(PsiElement e) {
    XmlTag target = PsiTreeUtil.getParentOfType(e, XmlTag.class);
    if (target == null) return Collections.emptyList();

    return Collections.singletonList(new Pair<PsiElement, Unwrapper>(target, new XmlEnclosingTagUnwrapper()));
  }
}
