package com.jetbrains.rest.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;

/**
 * User : ktisha
 */
public class RestLineManipulator extends AbstractElementManipulator<RestLine> {

  public RestLine handleContentChange(RestLine element, TextRange range, String newContent) {
    final String oldText = element.getText();
    final String newText = oldText.substring(0, range.getStartOffset()) + newContent + oldText.substring(range.getEndOffset());
    element.updateText(newText);
    return element;
  }

}
