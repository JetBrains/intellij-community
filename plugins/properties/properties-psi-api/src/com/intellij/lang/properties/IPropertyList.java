// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.psi.PsiComment;
import org.jetbrains.annotations.NotNull;

public interface IPropertyList {
  /**
   * The commented lines from the top to the first empty line from the top of the file
   * are considered as the property file's comment. This method extracts this text.
   *
   * @return the text that is inside {@link PsiComment} from the beginning of the file
   * to the first empty line from the top of the file or an empty string.
   */
  @NotNull String getDocCommentText();
}
