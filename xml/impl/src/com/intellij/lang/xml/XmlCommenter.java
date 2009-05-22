package com.intellij.lang.xml;

import com.intellij.lang.Commenter;

/**
 * @author max
 */
public class XmlCommenter implements Commenter {

  public String getLineCommentPrefix() {
    return null;
  }

  public String getBlockCommentPrefix() {
    return "<!--";
  }

  public String getBlockCommentSuffix() {
    return "-->";
  }

  public String getCommentedBlockCommentPrefix() {
    return "&lt;!&ndash;";
  }

  public String getCommentedBlockCommentSuffix() {
    return "&ndash;&gt;";
  }
}
