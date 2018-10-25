package org.jetbrains.yaml.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 */
public interface YAMLPsiElement extends NavigatablePsiElement {
  default List<YAMLPsiElement> getYAMLElements() {
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
