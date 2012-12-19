package org.jetbrains.yaml.psi;

import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

/**
 * @author oleg
 */
public interface YAMLKeyValue extends YAMLPsiElement, PsiNamedElement, PomTarget {
  YAMLKeyValue[] EMPTY_ARRAY = new YAMLKeyValue[0];
  
  PsiElement getKey();

  String getKeyText();

  PsiElement getValue();

  String getValueText();

  void setValueText(String text);

  String getValueIndent();
}