package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Dec 27, 2004
 * Time: 7:20:09 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class PsiExtendedTypeVisitor <X> extends PsiTypeVisitor<X> {
  public abstract X visitTypeVariable(PsiTypeVariable var);

  public X visitClassType(final PsiClassType classType) {
    super.visitClassType(classType);
    final PsiClassType.ClassResolveResult result = classType.resolveGenerics();

    if (result.getElement() != null) {
      for (Iterator<PsiType> t = result.getSubstitutor().getSubstitutionMap().values().iterator(); t.hasNext();) {
        final PsiType type = t.next();

        if (type != null) {
          type.accept(this);
        }
      }
    }

    return null;
  }
}
