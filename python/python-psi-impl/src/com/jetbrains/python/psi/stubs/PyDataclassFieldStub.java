/*
 * Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.psi.stubs;

import com.jetbrains.python.codeInsight.PyDataclassParameters;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.impl.stubs.PyDataclassMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface PyDataclassFieldStub extends CustomTargetExpressionStub {

  /**
   * @return name of the dataclass framework that declared this field, i.e.
   * {@link PyDataclassParameters.Type#getName()} of the provider that built
   * this stub. Matches {@link PyDataclassStub#getType()} of the owning class.
   */
  @NotNull String getType();

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

  /**
   * Opaque per-field payload written by the framework that built this stub, or {@code null} when it persisted nothing.
   * For built-in dataclass/attrs/transform fields this is always {@code null}. Decode it with
   * {@link PyDataclassMetadata#decode}, after checking {@link #getType()} names your framework.
   */
  @Nullable PyDataclassMetadata getMetadata();
}
