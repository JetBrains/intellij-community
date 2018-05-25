package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

public class YAMLBlockSequenceImpl extends YAMLSequenceImpl {
  public YAMLBlockSequenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitSequence(this);
    }
    else {
      super.accept(visitor);
    }
  }
}
