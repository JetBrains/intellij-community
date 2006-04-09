/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ide.errorTreeView;

import com.intellij.pom.Navigatable;

/**
 * @author Eugene Zhuravlev
 *         Date: Nov 12, 2004
 */
public class NavigatableMessageElement extends ErrorTreeElement{
  private final GroupingElement myParent;
  private final String[] myMessage;
  private Navigatable myNavigatable;
  private String myExportText;
  private String myRendererTextPrefix;

  public NavigatableMessageElement(ErrorTreeElementKind kind,
                                 GroupingElement parent,
                                 String[] message,
                                 Navigatable navigatable,
                                 String exportText,
                                 String rendererTextPrefix) {
    super(kind);
    myParent = parent;
    myMessage = message;
    myNavigatable = navigatable;
    myExportText = exportText;
    myRendererTextPrefix = rendererTextPrefix;
  }

  public Navigatable getNavigatable() {
    return myNavigatable;
  }

  public String[] getText() {
    return myMessage;
  }

  public Object getData() {
    return myParent.getData();
  }

  public GroupingElement getParent() {
    return myParent;
  }

  public String getExportTextPrefix() {
    return getKind().getPresentableText() + myExportText;
  }

  public String getRendererTextPrefix() {
    return myRendererTextPrefix;
  }
}
