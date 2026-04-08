// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.stubs;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.PyDataclassesKt;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.impl.stubs.PyCustomClassStub;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents dataclass-related properties directly available in a class definition, i.e. not considering its ancestor classes,
 * decorator parameter defaults or any other "external" configuration sources.
 * <p>
 * Note that omitted properties should have {@code null} value, not the default. These are substituted with the corresponding defaults
 * later during analysis after checking other possible sources.
 * <p>
 * To get a complete "merged" set of properties use {@link PyDataclassesKt#parseDataclassParameters(PyClass, TypeEvalContext)}.
 *
 * @see PyDataclassesKt#parseDataclassParameters(PyClass, TypeEvalContext)
 */
public interface PyDataclassStub extends PyCustomClassStub {

  /**
   * @return library used to determine dataclass.
   */
  @NotNull
  String getType();

  @Nullable QualifiedName decoratorName();

  /**
   * @return value of `init` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean initValue();

  /**
   * @return value of `repr` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean reprValue();

  /**
   * @return value of `eq` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean eqValue();

  /**
   * @return value of `order` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean orderValue();

  /**
   * @return value of `unsafe_hash` (std) or `hash` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean unsafeHashValue();

  /**
   * @return value of `frozen` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean frozenValue();

  /**
   * @return value of `matchArgs` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean matchArgsValue();

  /**
   * @return value of `kw_only` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean kwOnly();

  /**
   * @return value of `slots` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  @Nullable Boolean slotsValue();

  /**
   * Pydantic-specific flag propagated from model configuration.
   * <p>
   * For standard dataclasses, attrs, and generic dataclass_transform-based classes
   * this value is always {@code null}. It is only set for classes recognized
   * by {@link com.jetbrains.python.codeInsight.PyPydanticParametersProvider}
   * as Pydantic models.
   */
  @Nullable Boolean populateByName();
}
