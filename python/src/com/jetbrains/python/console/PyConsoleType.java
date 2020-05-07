// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.jetbrains.python.PyBundle;

public class PyConsoleType {
  public static final PyConsoleType PYTHON = new PyConsoleType("py", PyBundle.message("python.console"));

  private final String myTypeId;
  private final String myTitle;

  public PyConsoleType(String typeId, String title) {
    myTypeId = typeId;
    myTitle = title;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public String getTitle() {
    return myTitle;
  }
}
