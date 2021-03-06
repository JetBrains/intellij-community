// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.render;

import java.util.Set;

public abstract class AbstractIntegerRenderer implements PyNodeRenderer {
  private static final Set<String> mySupportedTypes = Set.of("int");

  @Override
  public boolean isApplicable(String type) {
    return type != null && mySupportedTypes.contains(type);
  }
}
