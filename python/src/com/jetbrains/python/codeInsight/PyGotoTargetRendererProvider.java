package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.navigation.GotoTargetRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyElement;

/**
 * @author yole
 */
public class PyGotoTargetRendererProvider implements GotoTargetRendererProvider {
  public PsiElementListCellRenderer getRenderer(final PsiElement[] elements) {
    for(PsiElement element: elements) {
      if (!(element instanceof PyElement) || !(element instanceof PsiNamedElement)) return null;
    }
    return new PyElementListCellRenderer();
  }

}
