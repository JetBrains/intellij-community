package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethod;

/**
 * @author Plushnikov Michail
 */
public class MyLightMethod extends LightMethod {

  private final PsiMethod myMethod;

  public MyLightMethod(PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass) {
    super(manager, valuesMethod, psiClass);
    myMethod = valuesMethod;
  }

  public PsiElement getParent() {
    PsiElement result = super.getParent();
    result = null != result ? result : getContainingClass();
    return result;
  }

  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    return containingClass != null ? containingClass.getContainingFile() : null;
  }

  public PsiElement copy() {
    return new MyLightMethod(myManager, (PsiMethod) myMethod.copy(), getContainingClass());
  }

  public ASTNode getNode() {
    return myMethod.getNode();
  }
}
