package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;

public abstract class JavaClassTreeElementBase<Value extends PsiElement> extends PsiTreeElementBase<Value> implements
                                                                          AccessLevelProvider {
  protected final boolean myIsInherited;

  protected JavaClassTreeElementBase(boolean isInherited, PsiElement element) {
    super(element);
    myIsInherited = isInherited;
  }

  public boolean isInherited() {
    return myIsInherited;
  }

  public boolean isPublic() {
    Value element = getElement();
    if (element instanceof PsiModifierListOwner) {
      return ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.PUBLIC);
    }
    else {
      return true;
    }
  }

  public int getAccessLevel() {
    return PsiUtil.getAccessLevel(((PsiModifierListOwner)getElement()).getModifierList());
  }

  public int getSubLevel() {
    return 0;
  }
}
