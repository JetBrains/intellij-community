package com.intellij.structuralsearch.impl.matcher.filters;

import com.intellij.psi.*;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 22, 2004
 * Time: 9:13:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class CommentFilter extends NodeFilter {
  public void visitComment(PsiComment comment) {
    result = true;
  }

  public void visitField(PsiField field) {
    result = true;
  }

  public void visitMethod(PsiMethod method) {
    result = true;
  }

  public void visitClass(PsiClass clazz) {
    result = true;
  }

  private static NodeFilter instance;

  public static NodeFilter getInstance() {
    if (instance==null) instance = new CommentFilter();
    return instance;
  }

  private CommentFilter() {
  }
}
