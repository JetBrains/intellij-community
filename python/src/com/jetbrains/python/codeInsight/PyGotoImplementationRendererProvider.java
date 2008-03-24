package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.navigation.GotoImplementationRendererProvider;
import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.psi.PyElement;

/**
 * @author yole
 */
public class PyGotoImplementationRendererProvider implements GotoImplementationRendererProvider {
  public PsiElementListCellRenderer getRenderer(final PsiElement[] elements) {
    for(PsiElement element: elements) {
      if (!(element instanceof PyElement) || !(element instanceof PsiNamedElement)) return null;
    }
    return new PyElementListCellRenderer();
  }

  public String getChooserTitle(final String name, final PsiElement[] elements) {
    return CodeInsightBundle.message("goto.implementation.chooser.title", name, elements.length);
  }
}
