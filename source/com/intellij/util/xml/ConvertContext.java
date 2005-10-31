/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiClass;

/**
 * @author peter
 */
public class ConvertContext {
  private final XmlFile myFile;

  public ConvertContext(final XmlFile file) {
    myFile = file;
  }

  public PsiClass findClass(String name) {
    return myFile.getManager().findClass(name, myFile.getResolveScope());
  }

  public XmlFile getFile() {
    return myFile;
  }


}
