package com.intellij.lang.java;

import com.intellij.lang.Commenter;

/**
 * @author max
 */
public class JavaCommenter implements Commenter {

  public String getLineCommentPrefix() {
    return "//";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }
}
