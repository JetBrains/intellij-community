package com.jetbrains.python.debugger;

/**
 * @author traff
 */
public class PyIo {
  private final String text;
  private final int ctx;

  public PyIo(String text, int ctx) {
    this.text = text;
    this.ctx = ctx;
  }

  public String getText() {
    return text;
  }

  public int getCtx() {
    return ctx;
  }
}
