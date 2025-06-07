// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.lang.Commenter;
import org.jetbrains.annotations.NonNls;

/**
 * @author Roman Chernyatchik
 */
public class YAMLCommenter implements Commenter {
  private static final @NonNls String LINE_COMMENT_PREFIX = "#";

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
