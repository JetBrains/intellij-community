package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.extapi.psi.ASTWrapperPsiElement;

public class SourceTreeToPsiMap {
  public static PsiElement treeElementToPsi(ASTNode element) {
    if (element == null) return null;
    return element.getPsi();
  }

  public static ASTNode psiElementToTree(PsiElement psiElement) {
    if (psiElement == null) return null;
    return psiElement.getNode();
  }

  public static boolean hasTreeElement(PsiElement psiElement) {
    return psiElement instanceof TreeElement || psiElement instanceof SrcRepositoryPsiElement || psiElement instanceof ASTWrapperPsiElement;
  }
}
