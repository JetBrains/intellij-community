package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public abstract class PyLineMarkerNavigator<T extends PsiElement> implements GutterIconNavigationHandler {
  public void navigate(final MouseEvent e, final PsiElement elt) {
    final List<NavigatablePsiElement> navElements = new ArrayList<NavigatablePsiElement>();
    Query<T> elementQuery = search(elt);
    if (elementQuery == null) return;
    elementQuery.forEach(new Processor<T>() {
      public boolean process(final T psiElement) {
        if (psiElement instanceof NavigatablePsiElement) {
          navElements.add((NavigatablePsiElement)psiElement);
        }
        return true;
      }
    });
    final NavigatablePsiElement[] methods = navElements.toArray(new NavigatablePsiElement[navElements.size()]);
    PsiElementListNavigator.openTargets(e, methods, getTitle(elt), new DefaultPsiElementCellRenderer());
  }

  protected abstract String getTitle(PsiElement elt);

  @Nullable
  protected abstract Query<T> search(PsiElement elt);
}
