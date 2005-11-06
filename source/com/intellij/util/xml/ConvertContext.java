/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;

/**
 * @author peter
 */
public interface ConvertContext {
  PsiClass findClass(String name);

  XmlTag getTag();

  XmlFile getFile();
}
