/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public abstract class ErrorTreeElement {
  private final ErrorTreeElementKind myKind;

  protected ErrorTreeElement() {
    this(ErrorTreeElementKind.GENERIC);
  }

  protected ErrorTreeElement(ErrorTreeElementKind kind) {
    myKind = kind;
  }

  public ErrorTreeElementKind getKind() {
    return myKind;
  }

  public abstract String[] getText();

  public abstract Object getData();

  public final String toString() {
    String[] text = getText();
    return text != null && text.length > 0? text[0] : "";
  }

  public abstract String getExportTextPrefix();
}
