package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;

public class YAMLBlockMappingImpl extends YAMLMappingImpl {
  public YAMLBlockMappingImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  protected void addNewKey(@NotNull YAMLKeyValue key) {
    final int indent = YAMLUtil.getIndentToThisElement(this);

    final YAMLElementGenerator generator = YAMLElementGenerator.getInstance(getProject());
    IElementType lastChildType = PsiUtilCore.getElementType(getLastChild());
    if (indent == 0) {
      if (lastChildType != YAMLTokenTypes.EOL) {
        add(generator.createEol());
      }
    }
    else if (!(lastChildType == YAMLTokenTypes.INDENT && getLastChild().getTextLength() == indent)) {
      add(generator.createEol());
      add(generator.createIndent(indent));
    }
    add(key);
  }
}
