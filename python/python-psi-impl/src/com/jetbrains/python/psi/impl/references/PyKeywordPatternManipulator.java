package com.jetbrains.python.psi.impl.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyKeywordPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyKeywordPatternManipulator extends AbstractElementManipulator<PyKeywordPattern> {
  @Override
  public @Nullable PyKeywordPattern handleContentChange(@NotNull PyKeywordPattern element,
                                                        @NotNull TextRange range,
                                                        String newContent) throws IncorrectOperationException {
    if (element.getKeywordElement().getTextRangeInParent().equals(range)) {
      PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
      String newPatternText = newContent + "=None";
      PyKeywordPattern newPattern = (PyKeywordPattern)generator.createPatternFromText(LanguageLevel.forElement(element), newPatternText);
      element.getKeywordElement().replace(newPattern.getKeywordElement());
      return element;
    }
    return null;
  }
}
