/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 1, 2001
 * Time: 11:42:56 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiDocCommentOwner;

public abstract class RefFilter {
  // Default accepts implementation accepts element if one under unaccepted one. Thus it will accept all and only upper level classes.
  public int getElementProblemCount(RefElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    final PsiElement element = refElement.getElement();
    if (element instanceof PsiDocCommentOwner && !InspectionManagerEx.isToCheckMember((PsiDocCommentOwner)element, "UnusedDeclaration")) return 0;
    RefEntity refOwner = refElement.getOwner();
    if (refOwner == null || !(refOwner instanceof RefElement)) return 1;

    return 1 - getElementProblemCount((RefElement)refOwner);
  }

  public final boolean accepts(RefElement refElement) {
    return getElementProblemCount(refElement) > 0;
  }
}
