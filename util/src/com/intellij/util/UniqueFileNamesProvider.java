/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import java.util.ArrayList;

public class UniqueFileNamesProvider {
  private final ArrayList<String> myExistingNames;

  public UniqueFileNamesProvider() {
    myExistingNames = new ArrayList<String>();
  }

  public String suggestName(String originalName) {
    String s = convertName(originalName);
    if (!contains(s)) {
      myExistingNames.add(s);
      return s;
    }

    for (int postfix = myExistingNames.size(); ; postfix++){
      String s1 = s + postfix;
      if (!contains(s1)) {
        myExistingNames.add(s1);
        return s1;
      }
    }
  }

  private boolean contains(String s) {
    for (int i = 0; i < myExistingNames.size(); i++) {
      if (myExistingNames.get(i).equalsIgnoreCase(s)) {
        return true;
      }
    }
    return false;
  }

  private String convertName(String s) {
    if (s == null || s.length() == 0) {
      return "_";
    }
    StringBuffer buf = new StringBuffer();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isJavaIdentifierPart(c) || c == ' ') {
        buf.append(c);
      }
      else {
        buf.append('_');
      }
    }
    return buf.toString();
  }
}
