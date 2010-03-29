package com.jetbrains.python;

import com.intellij.lang.Commenter;

/**
 * @author yole
 */
public class PythonCommenter implements Commenter {
  public String getLineCommentPrefix() {
    return "#";
  }

  public String getBlockCommentPrefix() {
    return null;
  }

  public String getBlockCommentSuffix() {
    return null;
  }

  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  public String getCommentedBlockCommentSuffix() {
    return null;
  }
}
