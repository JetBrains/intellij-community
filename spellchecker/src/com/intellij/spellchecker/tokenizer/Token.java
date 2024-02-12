// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.tokenizer;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;


public class Token<T extends PsiElement> {

  private final String text;
  private final boolean useRename;
  private int offset;


  private final T element;
  private final Splitter splitter;

  private TextRange range;

  public Token(T element, String text, boolean useRename, Splitter splitter) {
    this.element = element;
    this.text = text;
    this.useRename = useRename;
    this.splitter = splitter;
    this.offset = 0;
  }

  public Token(T element, String text, boolean useRename, int offset, Splitter splitter) {
    this(element, text, useRename, splitter);
    this.offset = offset;
  }

  public Token(T element, Splitter splitter) {
    this(element, element.getText(), false, splitter);
  }

  public Token(T element, boolean useRename, Splitter splitter) {
    this(element, element.getText(), useRename, splitter);
  }

  public Token(T element, String text, boolean useRename, int offset, TextRange textRange, Splitter splitter) {
    this(element, text, useRename, offset, splitter);
    this.range = textRange;
  }

  public String getText() {
    return text;
  }

  public T getElement() {
    return element;
  }

  public boolean isUseRename() {
    return useRename;
  }

  public int getOffset() {
    return offset;
  }

  public @NotNull TextRange getRange() {
    if (range == null) {
      range = new TextRange(0, (text != null ? text.length() : 0));
    }
    return range;
  }

  public void processAreas(Consumer<TextRange> consumer) {
    if (splitter == null || text == null) {
      return;
    }
    splitter.split(text, getRange(), consumer);
  }
}
