package com.intellij.ide.structureView.impl.java;

import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtil;

import javax.swing.*;

public abstract class JavaClassTreeElementBase extends PsiTreeElementBase implements
                                                                          AccessLevelProvider {
  protected final boolean myIsInherited;

  protected JavaClassTreeElementBase(boolean isInherited) {
    myIsInherited = isInherited;
  }

  public boolean isInherited() {
    return myIsInherited;
  }

  public boolean isPublic() {
    PsiElement element = getElement();
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
