package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiAnonymousClass;

/**
 * Created by IntelliJ IDEA.
 * User: maxim
 * Date: 26.12.2003
 * Time: 19:37:13
 * To change this template use Options | File Templates.
 */
public class ClassFilter extends NodeFilter {
  public void visitAnonymousClass(PsiAnonymousClass psiAnonymousClass) {
    result = true;
  }

  public void visitClass(PsiClass psiClass) {
    result = true;
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new ClassFilter();
    return instance;
  }

  private ClassFilter() {
  }
}
