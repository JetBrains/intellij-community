package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;

public class SourceTreeToPsiMap {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.SourceTreeToPsiMap");

  public static PsiElement treeElementToPsi(ASTNode element) {
    ProgressManager.getInstance().checkCanceled();

    if (element == null) return null;

    if (element instanceof PsiElement) {
      return (PsiElement)element;
    }
    else if (element instanceof RepositoryTreeElement) {
      return RepositoryElementsManager.getOrFindPsiElement((RepositoryTreeElement)element);
    }
    else {
      //TODO!!!
      PsiElement psiElement = element.getUserData(TreeElement.PSI_ELEMENT_KEY);
      if (psiElement != null) return psiElement;
      final Language lang = element.getElementType().getLanguage();
      if (lang != null) {
        psiElement = lang.getParserDefinition().createElement(element);
        element.putUserData(TreeElement.PSI_ELEMENT_KEY, psiElement);
        return psiElement;
      }
      LOG.error("Not PsiElement:" + element);
      return null;
    }
  }

  public static ASTNode psiElementToTree(PsiElement psiElement) {
    ProgressManager.getInstance().checkCanceled();

    if (psiElement == null) return null;

    if (psiElement instanceof TreeElement) {
      return (TreeElement)psiElement;
    }
    else if (psiElement instanceof SrcRepositoryPsiElement) {
      return RepositoryElementsManager.getOrFindTreeElement((SrcRepositoryPsiElement)psiElement);
    }
    else if (psiElement instanceof ASTWrapperPsiElement) {
      return ((ASTWrapperPsiElement)psiElement).getNode();
    }
    else {
      LOG.error("Not TreeElement:" + psiElement);
      return null;
    }
  }

  public static boolean hasTreeElement(PsiElement psiElement) {
    return psiElement instanceof TreeElement || psiElement instanceof SrcRepositoryPsiElement;
  }
}
