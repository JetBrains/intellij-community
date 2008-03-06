package com.intellij.lang.xml;

import com.intellij.codeInsight.unwrap.UnwrapDescriptor;
import com.intellij.codeInsight.unwrap.Unwrapper;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;

import java.util.Collections;
import java.util.List;

public class XmlUnwrapDescriptor implements UnwrapDescriptor {
  public List<Pair<PsiElement, Unwrapper>> collectUnwrappers(Project project, Editor editor, PsiFile file) {
    PsiElement e = findElement(editor, file);
    if (e == null) return Collections.emptyList();

    return Collections.singletonList(new Pair<PsiElement, Unwrapper>(e, new XmlEnclosingTagUnwrapper()));
  }

  private PsiElement findElement(Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement e = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(e, XmlTag.class);
  }

  public boolean showOptionsDialog() {
    return false;
  }
}
