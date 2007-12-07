package com.intellij.psi.impl.light;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class LightTypeElement extends LightElement implements PsiTypeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.light.LightTypeElement");

  private final PsiType myType;

  public LightTypeElement(PsiManager manager, PsiType type) {
    super(manager);
    type = PsiUtil.convertAnonymousToBaseType(type);
    myType = type;
  }

  public String toString() {
    return "PsiTypeElement:" + getText();
  }

  public String getText() {
    return myType.getPresentableText();
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public PsiElement copy() {
    return new LightTypeElement(myManager, myType);
  }

  @NotNull
  public PsiType getType() {
    return myType;
  }

  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    return null;
  }

  public boolean isValid() {
    return myType.isValid();
  }
}
