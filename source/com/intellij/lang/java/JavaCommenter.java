package com.intellij.lang.java;

import com.intellij.lang.Commenter;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public class JavaCommenter implements Commenter {
  public IElementType getLineCommentToken() {
    return JavaTokenType.END_OF_LINE_COMMENT;
  }

  public String getLineCommentPrefix() {
    return "//";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public IElementType getBlockCommentToken() {
    return JavaTokenType.C_STYLE_COMMENT;
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }
}
