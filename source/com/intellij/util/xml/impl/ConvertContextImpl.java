/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;

/**
 * @author peter
 */
public class ConvertContextImpl implements ConvertContext {
  private final XmlTag myTag;
  private final XmlFile myFile;

  public ConvertContextImpl(final DomInvocationHandler handler) {
    myFile = handler.getFile();
    myTag = handler.getXmlTag();
  }

  public final PsiClass findClass(String name) {
    final XmlFile file = getFile();
    return file.getManager().findClass(name, file.getResolveScope());
  }

  public final XmlTag getTag() {
    return myTag;
  }

  public final XmlFile getFile() {
    return myFile;
  }


}
