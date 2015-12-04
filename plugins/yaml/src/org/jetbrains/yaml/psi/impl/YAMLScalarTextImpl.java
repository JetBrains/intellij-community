package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLScalarText;

/**
 * @author oleg
 */
public class YAMLScalarTextImpl extends YAMLBlockScalarImpl implements YAMLScalarText {
  public YAMLScalarTextImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  protected IElementType getContentType() {
    return YAMLTokenTypes.SCALAR_TEXT;
  }

  @NotNull
  @Override
  protected String getRangesJoiner(@NotNull CharSequence leftString, @NotNull CharSequence rightString) {
    if (StringUtil.isEmptyOrSpaces(leftString)) {
      return "\n";
    }
    if (StringUtil.startsWithChar(leftString, ' ') || StringUtil.startsWithChar(leftString, '\t')
      || StringUtil.startsWithChar(rightString, ' ') || StringUtil.startsWithChar(rightString, '\t')) {
      return "\n";
    }
    if (StringUtil.isEmptyOrSpaces(rightString)) {
      return "";
    }
    return " ";
  }

  @NotNull
  @Override
  public String getTextValue() {
    return super.getTextValue() + "\n";
  }

  @Override
  public String toString() {
    return "YAML scalar text";
  }

}