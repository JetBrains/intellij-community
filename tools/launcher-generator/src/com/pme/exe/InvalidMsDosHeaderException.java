// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.exe;

import java.io.IOException;

/**
 * @author Sergey Zhulin
 * Date: Mar 31, 2006
 * Time: 2:09:25 PM
 */
public class InvalidMsDosHeaderException extends IOException {
  public InvalidMsDosHeaderException() {
  }

  public InvalidMsDosHeaderException(String s) {
    super(s);
  }
}
