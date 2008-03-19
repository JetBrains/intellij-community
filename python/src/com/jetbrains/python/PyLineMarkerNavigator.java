package com.jetbrains.python;

import com.intellij.codeInsight.daemon.impl.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.psi.PsiElement;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.search.PySuperMethodsSearch;

import java.awt.event.MouseEvent;

/**
 * @author yole
 */
public class PyLineMarkerNavigator implements GutterIconNavigationHandler {
  public void navigate(final MouseEvent e, final PsiElement elt) {
    if (elt.getParent() instanceof PyFunction) {
      final PyFunction function = (PyFunction)elt.getParent();
      final PyFunction[] methods = PySuperMethodsSearch.search(function).toArray(new PyFunction[0]); 
      PsiElementListNavigator.openTargets(e, methods, "Choose Super Method of " + function.getName(),
                                          new DefaultPsiElementCellRenderer());
    }
  }
}
