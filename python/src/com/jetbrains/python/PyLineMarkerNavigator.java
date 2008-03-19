package com.jetbrains.python;

import com.intellij.codeInsight.daemon.impl.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;

import java.awt.event.MouseEvent;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public class PyLineMarkerNavigator implements GutterIconNavigationHandler {
  public void navigate(final MouseEvent e, final PsiElement elt) {
    if (elt.getParent() instanceof PyFunction) {
      final PyFunction function = (PyFunction)elt.getParent();
      final List<NavigatablePsiElement> navElements = new ArrayList<NavigatablePsiElement>();
      PySuperMethodsSearch.search(function).forEach(new Processor<PsiElement>() {
        public boolean process(final PsiElement psiElement) {
          if (psiElement instanceof NavigatablePsiElement) {
            navElements.add((NavigatablePsiElement) psiElement);
          }
          return true;
        }
      });
      final NavigatablePsiElement[] methods = navElements.toArray(new NavigatablePsiElement[navElements.size()]); 
      PsiElementListNavigator.openTargets(e, methods, "Choose Super Method of " + function.getName(),
                                          new DefaultPsiElementCellRenderer());
    }
  }
}
