package org.jetbrains.yaml.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLPsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public class YAMLPsiElementImpl extends ASTWrapperPsiElement implements YAMLPsiElement {
  public YAMLPsiElementImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML element";
  }

  public List<YAMLPsiElement> getYAMLElements() {
    final ArrayList<YAMLPsiElement> result = new ArrayList<>();
    for (ASTNode node : getNode().getChildren(null)) {
      final PsiElement psi = node.getPsi();
      if (psi instanceof YAMLPsiElement){
        result.add((YAMLPsiElement) psi);
      }
    }
    return result;
  }
}
