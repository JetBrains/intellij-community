/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

public class WriteExternalException extends Exception {
  public WriteExternalException(){
    super();
  }

  public WriteExternalException(String s){
    super(s);
  }
}
