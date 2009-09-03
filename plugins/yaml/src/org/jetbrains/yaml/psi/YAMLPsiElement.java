package org.jetbrains.yaml.psi;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author oleg
 */
public interface YAMLPsiElement extends PsiElement {
  List<YAMLPsiElement> getYAMLElements();
}
