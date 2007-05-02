package com.intellij.lang.xml;

import com.intellij.lang.Commenter;

/**
 * @author max
 */
public class XmlCommenter implements Commenter {

  public String getLineCommentPrefix() {
    return null;
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public String getBlockCommentPrefix() {
    return "<!--";
  }

  public String getBlockCommentSuffix() {
    return "-->";
  }
}
