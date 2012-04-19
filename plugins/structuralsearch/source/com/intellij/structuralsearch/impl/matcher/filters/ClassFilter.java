package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.dupLocator.util.NodeFilter;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 19:37:13
 * To change this template use Options | File Templates.
 */
public class ClassFilter extends JavaElementVisitor implements NodeFilter {
  protected boolean result;

  @Override public void visitAnonymousClass(PsiAnonymousClass psiAnonymousClass) {
    result = true;
  }

  @Override public void visitClass(PsiClass psiClass) {
    result = true;
  }

  private static class NodeFilterHolder {
    private static final NodeFilter instance = new ClassFilter();
  }

  public static NodeFilter getInstance() {
    return NodeFilterHolder.instance;
  }

  private ClassFilter() {
  }

  public boolean accepts(PsiElement element) {
    result = false;
    if (element!=null) element.accept(this);
    return result;
  }
}
