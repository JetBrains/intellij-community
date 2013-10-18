package com.jetbrains.rest.psi;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

/**
 * User : catherine
 */
public class RestTitle extends RestElement {
  public RestTitle(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestTitle:" + getNode().getElementType().toString();
  }

  @Nullable
  public String getName() {
    final String text = getNode().getText();
    if (text.length() == 0) return null;
    final char adorn = text.charAt(text.length()-2);
    final CharacterIterator it = new StringCharacterIterator(text);
    int finish = 0;
    for (char ch = it.last(); ch != CharacterIterator.DONE; ch = it.previous()) {
      if (finish == 0)
        finish++;
      else if (ch != adorn) {
        finish = it.getIndex();
        break;
      }
    }
    int start = 0;
    if (text.charAt(0) == adorn) {
      for (char ch = it.first(); ch != CharacterIterator.DONE; ch = it.next()) {
        if (ch != adorn) {
          start = it.getIndex() + 1;
          break;
        }
      }
    }
    if (finish <= 0 || start < 0)
      return null;
    return text.substring(start, finish).trim();
  }
}
