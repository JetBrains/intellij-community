/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiClass;

/**
 * @author peter
 */
public class PsiClassConverter implements Converter<PsiClass>{
  public PsiClass fromString(final String s, final ConvertContext context) throws ConvertFormatException {
    return context.findClass(s);
  }

  public String toString(final PsiClass t, final ConvertContext context) {
    return t.getQualifiedName();
  }
}
