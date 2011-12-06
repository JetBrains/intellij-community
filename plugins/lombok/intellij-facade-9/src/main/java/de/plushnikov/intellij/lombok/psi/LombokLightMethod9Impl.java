package de.plushnikov.intellij.lombok.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.light.LightMethod;

/**
 * @author Plushnikov Michail
 */
public class LombokLightMethod9Impl extends LightMethod implements LombokLightMethod {

  private final PsiMethod myMethod;

  public LombokLightMethod9Impl(PsiManager manager, PsiMethod valuesMethod, PsiClass psiClass) {
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
    return new LombokLightMethod9Impl(myManager, (PsiMethod) myMethod.copy(), getContainingClass());
  }

  public ASTNode getNode() {
    return myMethod.getNode();
  }
}
