package com.intellij.lang.xml;

import com.intellij.lang.Commenter;
import com.intellij.psi.tree.IElementType;

/**
 * @author max
 */
public class XmlCommenter implements Commenter {
  public IElementType getLineCommentToken() {
    return null;
  }

  public String getLineCommentPrefix() {
    return null;
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public IElementType getBlockCommentToken() {
    return null;
  }

  public String getBlockCommentPrefix() {
    return "<!--";
  }

  public String getBlockCommentSuffix() {
    return "-->";
  }
}
