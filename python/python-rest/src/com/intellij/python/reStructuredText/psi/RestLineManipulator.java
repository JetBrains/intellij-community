// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class RestLineManipulator extends AbstractElementManipulator<RestLine> {

  @Override
  public RestLine handleContentChange(@NotNull RestLine element, @NotNull TextRange range, String newContent) {
    final String oldText = element.getText();
    final String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    element.updateText(newText);
    return element;
  }

}
