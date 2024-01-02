// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.python.reStructuredText.validation.RestElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

/**
 * User : catherine
 */
public class RestTitle extends RestElement {
  private static final String ourAdornmentSymbols = "=-`:.'\\~^_*+#>";

  public RestTitle(@NotNull final ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    return "RestTitle:" + getNode().getElementType().toString();
  }

  @Override
  @Nullable
  public String getName() {
    final String text = getNode().getText().trim();
    if (text.length() < 2) return null;
    final char adorn = text.charAt(text.length()-2);
    final CharacterIterator it = new StringCharacterIterator(text);
    int finish = 0;
    for (char ch = it.last(); ch != CharacterIterator.DONE; ch = it.previous()) {
      if (ch != adorn) {
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

  @Nullable
  public String getOverline() {
    final String text = getNode().getText().trim();
    final Pair<Character, Character> adornments = getAdornments();
    final Character overlineChar = adornments.getFirst();
    if (overlineChar == null) return null;
    int end = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) != overlineChar) {
        end = i;
        break;
      }
    }
    return text.substring(0, end);
  }

  @Nullable
  public String getUnderline() {
    final String text = getNode().getText().trim();
    if (text.length() < 2) return null;
    final char adorn = text.charAt(text.length()-1);
    final CharacterIterator it = new StringCharacterIterator(text);
    int start = 0;
    for (char ch = it.last(); ch != CharacterIterator.DONE; ch = it.previous()) {
      if (ch != adorn) {
        start = it.getIndex() + 1;
        break;
      }
    }
    return text.substring(start);
  }

  public Pair<Character, Character> getAdornments() {
    final String text = getNode().getText().trim();
    if (text.length() < 2) return Pair.empty();
    Character overline = text.charAt(0);
    if (text.split("\n").length < 3 || ourAdornmentSymbols.indexOf(overline) < 0) {
      overline = null;
    }
    final char underline = text.charAt(text.length()-2);
    return Pair.create(overline, underline);
  }

  @Override
  protected void acceptRestVisitor(RestElementVisitor visitor) {
    visitor.visitTitle(this);
  }
}
