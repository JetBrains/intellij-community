/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class SimpleMessageElement extends ErrorTreeElement{
  private final String[] myMessage;
  private final Object myData;

  public SimpleMessageElement(ErrorTreeElementKind kind, String[] text, Object data) {
    super(kind);
    myMessage = text;
    myData = data;
  }

  public String[] getText() {
    return myMessage;
  }

  public Object getData() {
    return myData;
  }

  public String getExportTextPrefix() {
    return "";
  }
}
