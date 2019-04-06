/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;

public interface PyDataclassFieldStub extends CustomTargetExpressionStub {

  /**
   * @return true if default value is specified, false otherwise.
   */
  boolean hasDefault();

  /**
   * @return true if factory providing default value is specified, false otherwise.
   */
  boolean hasDefaultFactory();

  /**
   * @return true if field is used in `__init__`.
   */
  boolean initValue();
}
