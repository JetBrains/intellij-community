/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.j2ee.j2eeDom.xmlData.ReadOnlyDeploymentDescriptorModificationException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.Converter;

/**
 * @author peter
 */
public abstract class SetInvocation implements Invocation {
  private Converter myConverter;

  protected SetInvocation(final Converter converter) {
    myConverter = converter;
  }

  public Object invoke(final DomInvocationHandler handler, final Object[] args) throws Throwable {
    assert handler.isValid();
    final VirtualFile virtualFile = handler.getFile().getVirtualFile();
    if (virtualFile != null && !virtualFile.isWritable()) {
      throw new ReadOnlyDeploymentDescriptorModificationException(virtualFile);
    }

    final DomManagerImpl manager = handler.getManager();
    final boolean changing = manager.setChanging(true);
    try {
      if (args[0] == null) {
        handler.undefine();
      } else {
        setValue(handler, myConverter.toString(args[0], new ConvertContextImpl(handler)));
      }
    }
    finally {
      manager.setChanging(changing);
    }
    return null;
  }

  protected abstract void setValue(DomInvocationHandler handler, String value) throws IncorrectOperationException;

}
