package com.intellij.refactoring.typeCook.deductive;

import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Jan 12, 2005
 * Time: 9:41:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiTypeVariableFactory {
  private int myCurrent = 0;

  public final int getNumber() {
    return myCurrent;
  }

  public final PsiTypeVariable create(){
    return new PsiTypeVariable (){
        private int myIndex = myCurrent++;

        public String getPresentableText() {
          return "$" + myIndex;
        }

        public String getCanonicalText() {
          return getPresentableText();
        }

        public String getInternalCanonicalText() {
          return getCanonicalText();
        }

        public boolean isValid() {
          return true;
        }

        public boolean equalsToText(String text) {
          return text.equals(getPresentableText());
        }

        public <A> A accept(PsiTypeVisitor<A> visitor) {
          if (visitor instanceof PsiExtendedTypeVisitor) {
            return ((PsiExtendedTypeVisitor<A>)visitor).visitTypeVariable(this);
          }

          return null;
        }

        public GlobalSearchScope getResolveScope() {
          return null;
        }

        public PsiType[] getSuperTypes() {
          return new PsiType[0];
        }

        public boolean equals(Object o) {
          if (this == o) return true;
          if (!(o instanceof PsiTypeVariable)) return false;

          final PsiTypeVariable psiTypeVariable = (PsiTypeVariable)o;

          if (myIndex != psiTypeVariable.getIndex()) return false;

          return true;
        }

        public int hashCode() {
          return myIndex;
        }

        public int getIndex() {
          return myIndex;
        }
    };
  }
}
