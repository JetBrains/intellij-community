package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

/**
 * @author oleg
 */
public interface YAMLKeyValue extends YAMLPsiElement, PsiNamedElement {
  
  PsiElement getKey();

  String getKeyText();

  PsiElement getValue();

  String getValueText();

  String getValueIndent();
}