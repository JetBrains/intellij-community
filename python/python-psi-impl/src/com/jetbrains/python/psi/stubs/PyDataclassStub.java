// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.psi.impl.stubs.PyCustomClassStub;
import org.jetbrains.annotations.NotNull;

public interface PyDataclassStub extends PyCustomClassStub {

  /**
   * @return library used to determine dataclass.
   */
  @NotNull
  String getType();

  /**
   * @return value of `init` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean initValue();

  /**
   * @return value of `repr` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean reprValue();

  /**
   * @return value of `eq` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean eqValue();

  /**
   * @return value of `order` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean orderValue();

  /**
   * @return value of `unsafe_hash` (std) or `hash` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean unsafeHashValue();

  /**
   * @return value of `frozen` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean frozenValue();

  /**
   * @return value of `kw_only` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  boolean kwOnly();
}
