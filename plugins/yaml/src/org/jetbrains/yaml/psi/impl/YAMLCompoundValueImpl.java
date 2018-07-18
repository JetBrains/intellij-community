package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLCompoundValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

/**
 * @author oleg
 */
public class YAMLCompoundValueImpl extends YAMLValueImpl implements YAMLCompoundValue {
  public YAMLCompoundValueImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "YAML compound value";
  }

  @NotNull
  @Override
  public String getTextValue() {
    PsiElement element = getTag() != null ? getTag().getNextSibling() : getFirstChild();

    while (element != null && !(element instanceof YAMLScalar)) {
      element = element.getNextSibling();
    }

    if (element != null) {
      return ((YAMLScalar)element).getTextValue();
    }
    else {
      return "<compoundValue:" + Integer.toHexString(getText().hashCode()) + ">";
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitCompoundValue(this);
    }
    else {
      super.accept(visitor);
    }
  }
}