package com.intellij.refactoring.typeCook;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 18.12.2003
 * Time: 18:55:48
 * To change this template use Options | File Templates.
 */
public class Bottom extends PsiType {
  public final static Bottom BOTTOM = new Bottom();

  private Bottom() {

  }

  public String getPresentableText() {
    return "_";
  }

  public String getCanonicalText() {
    return "_";
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  public boolean isValid() {
    return true;
  }

  public boolean equalsToText(String text) {
    return text.equals("_");
  }

  public boolean equals(Object o) {
    if (o instanceof Bottom) {
      return true;
    }

    return false;
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return null;
  }

  public PsiType[] getSuperTypes() {
    throw new UnsupportedOperationException();
  }

  public GlobalSearchScope getResolveScope() {
    return null;
  }
}
