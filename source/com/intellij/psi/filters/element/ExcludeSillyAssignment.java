package com.intellij.psi.filters.element;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.FilterUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 06.01.2004
 * Time: 17:59:58
 * To change this template use Options | File Templates.
 */
public class ExcludeSillyAssignment implements ElementFilter {
  public boolean isAcceptable(Object element, PsiElement context) {
    if(!(element instanceof PsiElement)) return true;
    final PsiElement previousElement = FilterUtil.getPreviousElement(context, false);
    if(previousElement==null || !"=".equals(previousElement.getText())) return true;
    final PsiElement id = FilterUtil.getPreviousElement(previousElement, false);
    if(id instanceof PsiIdentifier && id.getParent() instanceof PsiReference){
      final PsiElement resolve = ((PsiReference)id.getParent()).resolve();
      if(resolve != null && context.getManager().areElementsEquivalent((PsiElement)element, resolve)) return false;
    }
    return true;
  }

  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }
}
