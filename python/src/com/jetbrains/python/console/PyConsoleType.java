// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;

public class PyConsoleType {
  public static final PyConsoleType PYTHON = new PyConsoleType("py", PyBundle.message("python.console"));

  private final String myTypeId;
  private final @Nls String myTitle;

  public PyConsoleType(String typeId, @Nls String title) {
    myTypeId = typeId;
    myTitle = title;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public @Nls String getTitle() {
    return myTitle;
  }
}
