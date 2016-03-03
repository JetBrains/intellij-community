package org.jetbrains.yaml.psi;

import com.intellij.psi.NavigatablePsiElement;

import java.util.List;

/**
 * @author oleg
 */
public interface YAMLPsiElement extends NavigatablePsiElement {
  List<YAMLPsiElement> getYAMLElements();
}
