/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;
import com.intellij.util.xml.Distinguisher;
import com.intellij.util.xml.DomElement;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class AddChildInvocation implements Invocation{
  private Type myType;
  private String myTagName;
  private int myStartIndex;
  private final Distinguisher myDistinguisher;

  public AddChildInvocation(final Type type, final String tagName, final int startIndex, final Distinguisher distinguisher) {
    myType = type;
    myTagName = tagName;
    myStartIndex = startIndex;
    myDistinguisher = distinguisher;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    final VirtualFile virtualFile = handler.getFile().getVirtualFile();
    if (virtualFile != null && !virtualFile.isWritable()) {
      throw new ReadOnlyDeploymentDescriptorModificationException(virtualFile);
    }
    int index = args.length == 0 ? Integer.MAX_VALUE : myStartIndex + (Integer)args[0];
    final DomElement domElement = handler.addChild(myTagName, myType, index);
    if (myDistinguisher != null) {
      final boolean b = handler.getManager().setChanging(true);
      try {
        myDistinguisher.distinguish(domElement.getXmlTag());
      }
      finally {
        handler.getManager().setChanging(b);
      }
    }
    return domElement;
  }
}
