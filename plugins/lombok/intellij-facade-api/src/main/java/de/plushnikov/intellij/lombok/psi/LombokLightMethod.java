package de.plushnikov.intellij.lombok.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

/**
 * @author Plushnikov Michail
 */
public interface LombokLightMethod extends PsiMethod {
  LombokLightMethod withNavigationElement(PsiElement navigationElement);
}
