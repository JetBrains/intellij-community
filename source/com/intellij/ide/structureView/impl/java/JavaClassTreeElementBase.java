package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;

public abstract class JavaClassTreeElementBase<Value extends PsiElement> extends PsiTreeElementBase<Value> implements AccessLevelProvider {
  private final boolean myIsInherited;

  protected JavaClassTreeElementBase(boolean isInherited, Value element) {
    super(element);
    myIsInherited = isInherited;
  }

  public boolean isInherited() {
    return myIsInherited;
  }

  public boolean isPublic() {
    Value element = getElement();
    return !(element instanceof PsiModifierListOwner) || ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PUBLIC);
  }

  public int getAccessLevel() {
    return PsiUtil.getAccessLevel(((PsiModifierListOwner)getElement()).getModifierList());
  }

  public int getSubLevel() {
    return 0;
  }

  public boolean equals(final Object o) {
    if (!super.equals(o)) return false;
    final JavaClassTreeElementBase that = (JavaClassTreeElementBase)o;

    if (myIsInherited != that.myIsInherited) return false;

    return true;
  }
}
