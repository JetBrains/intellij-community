// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleTagManipulator<T extends XmlTag> extends AbstractElementManipulator<T> {
  @Override
  public T handleContentChange(@NotNull T tag, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    final StringBuilder replacement = new StringBuilder( tag.getValue().getText() );
    final int valueOffset = tag.getValue().getTextRange().getStartOffset() - tag.getTextOffset();

    replacement.replace(
      range.getStartOffset() - valueOffset,
      range.getEndOffset() - valueOffset,
      newContent
    );
    tag.getValue().setEscapedText(replacement.toString());
    return tag;
  }
}
