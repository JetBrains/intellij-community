package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLValue;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

abstract class YAMLValueImpl extends YAMLPsiElementImpl implements YAMLValue {
  public YAMLValueImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Nullable
  @Override
  public PsiElement getTag() {
    final PsiElement firstChild = getFirstChild();
    if (firstChild.getNode().getElementType() == YAMLTokenTypes.TAG) {
      return firstChild;
    }
    else {
      return null;
    }
  }

  @Override
  public String toString() {
    return "YAML value";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitValue(this);
    }
    else {
      super.accept(visitor);
    }
  }
}
