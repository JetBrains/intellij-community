// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public interface PyAstPattern extends PyAstElement {
  /**
   * An irrefutable pattern is a pattern that always succeed (matches).
   * <p>
   * Primarily, these are capture patterns and wildcard patterns, as well as group and OR-patterns containing one of those.
   * <p>
   * <h3>Examples of irrefutable patterns:</h3>
   * <ul>
   *   <li>{@code name}</li>
   *   <li>{@code _} (a wildcard)</li>
   *   <li>{@code 42 | name}</li>
   *   <li>{@code (42 | name) as alias}</li>
   *   <li>{@code ((name))}</li>
   *   <li>{@code *args}</li>
   *   <li>{@code **kwargs}</li>
   * </ul>
   * <p>
   * <h3>Examples of refutable patterns:</h3>
   * <ul>
   *   <li>{@code foo.bar}</li>
   *   <li>{@code 'foo'}</li>
   *   <li>{@code [_]}</li>
   *   <li>{@code [*args]}</li>
   *   <li>{@code str(foo)}</li>
   * </ul>
   */
  boolean isIrrefutable();
}
