package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.List;

public abstract class YAMLSequenceImpl extends YAMLCompoundValueImpl implements YAMLSequence {
  public YAMLSequenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  public List<YAMLSequenceItem> getItems() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, YAMLSequenceItem.class);
  }

  @NotNull
  @Override
  public String getTextValue() {
    return "<sequence:" + Integer.toHexString(getText().hashCode()) + ">";
  }

  @Override
  public String toString() {
    return "YAML sequence";
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
