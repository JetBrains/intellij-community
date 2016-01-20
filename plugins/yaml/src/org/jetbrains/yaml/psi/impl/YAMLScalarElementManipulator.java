package org.jetbrains.yaml.psi.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLScalar;

public class YAMLScalarElementManipulator extends AbstractElementManipulator<YAMLScalar> {

  @NotNull
  @Override
  public TextRange getRangeInElement(@NotNull YAMLScalar element) {
    final String content = element.getText();
    final int startOffset = content.startsWith("'") || content.startsWith("\"") ? 1 : 0;
    final int endOffset = content.length() > 1 && (content.endsWith("'") || content.endsWith("\"")) ? -1 : 0;
    return new TextRange(startOffset, content.length() + endOffset);
  }

  @Override
  public YAMLScalar handleContentChange(@NotNull YAMLScalar element, @NotNull TextRange range, String newContent)
    throws IncorrectOperationException {
    return element;
  }
}
