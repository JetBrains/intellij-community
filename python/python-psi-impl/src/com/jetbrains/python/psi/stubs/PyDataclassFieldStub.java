/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Whether the corresponding field should be used in `__init__` as a keyword-only parameter.
   * <p>
   * When {@code null}, this property of the field depends on the value of {@code kw_only} argument of 
   * a {@code @dataclass_transform}-powered decorator or a base class and {@code kw_only_default} parameter default 
   * of {@code @dataclass_transform} itself.
   */
  @Nullable Boolean kwOnly();

  @Nullable String getAlias();
}
