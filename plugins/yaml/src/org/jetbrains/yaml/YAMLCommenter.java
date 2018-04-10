package org.jetbrains.yaml;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.NonNls;

/**
 * @author Roman Chernyatchik
 */
public class YAMLCommenter implements Commenter {
  @NonNls
  private static final String LINE_COMMENT_PREFIX = "#";

  @Override
  public String getLineCommentPrefix() {
    return LINE_COMMENT_PREFIX;
  }

  @Override
  public String getBlockCommentPrefix() {
    // N/A
    return null;
  }

  @Override
  public String getBlockCommentSuffix() {
    // N/A
    return null;
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }
}
