package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.PsiTypeVisitor;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Dec 27, 2004
 * Time: 7:20:09 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class PsiExtendedTypeVisitor <X> extends PsiTypeVisitor<X> {
  public abstract X visitTypeVariable(PsiTypeVariable var);
}
