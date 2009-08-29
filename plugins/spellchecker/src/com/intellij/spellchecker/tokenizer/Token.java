package com.intellij.spellchecker.tokenizer;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class Token<T extends PsiElement> {

  private String text;
  private String description;
  private T element;
  private boolean useRename;
  private int offset;

  public Token(T element, String text, boolean useRename) {
    this.element = element;
    this.text = text;
    this.useRename = useRename;
  }

  public Token(T element, String text, boolean useRename, int offset) {
    this.element = element;
    this.text = text;
    this.useRename = useRename;
    this.offset = offset;
  }

  public Token(T element, String text, String description, boolean useRename) {
    this.element = element;
    this.text = text;
    this.description = description;
    this.useRename = useRename;
  }

  public String getText() {
    return text;
  }

  public String getDescription() {
    return description;
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
}
