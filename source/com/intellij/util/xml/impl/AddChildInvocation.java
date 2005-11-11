/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AddChildInvocation implements Invocation{
  private Type myType;
  private String myTagName;
  private int myStartIndex;

  public AddChildInvocation(final Type type, final String tagName, final int startIndex) {
    myType = type;
    myTagName = tagName;
    myStartIndex = startIndex;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final VirtualFile virtualFile = handler.getFile().getVirtualFile();
    if (virtualFile != null && !virtualFile.isWritable()) {
      throw new ReadOnlyDeploymentDescriptorModificationException(virtualFile);
    }
    int index = args.length == 0 ? Integer.MAX_VALUE : myStartIndex + (Integer)args[0];
    return handler.addChild(myTagName, myType, index);
  }
}
