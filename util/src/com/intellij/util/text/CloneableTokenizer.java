/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.text;

import java.util.StringTokenizer;

/**
 * @author mike
 */
public class CloneableTokenizer extends StringTokenizer implements Cloneable {
  public CloneableTokenizer(String str) {
    super(str);
  }

  public CloneableTokenizer(String str, String delim) {
    super(str, delim);
  }

  public CloneableTokenizer(String str, String delim, boolean returnDelims) {
    super(str, delim, returnDelims);
  }

  public Object clone() {
    try {
      return super.clone();
    }
    catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }

    return null;
  }
}
