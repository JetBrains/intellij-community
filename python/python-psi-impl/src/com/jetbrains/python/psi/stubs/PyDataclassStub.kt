package com.jetbrains.python.psi.stubs

import com.intellij.psi.util.QualifiedName
import com.jetbrains.python.psi.impl.stubs.PyCustomClassStub
import com.jetbrains.python.psi.impl.stubs.PyDataclassMetadata

/**
 * Represents dataclass-related properties directly available in a class definition, i.e. not considering its ancestor classes,
 * decorator parameter defaults or any other "external" configuration sources.
 * <p>
 * Note that omitted properties should have {@code null} value, not the default. These are substituted with the corresponding defaults
 * later during analysis after checking other possible sources.
 * <p>
 * To get a complete "merged" set of properties use {@link PyDataclassesKt#parseDataclassParameters(PyClass, TypeEvalContext)}.
 */
interface PyDataclassStub : PyCustomClassStub {

  /**
   * @return library used to determine dataclass.
   */
  val type: String

  fun decoratorName(): QualifiedName?

  /**
   * @return value of `init` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun initValue(): Boolean?

  /**
   * @return value of `repr` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun reprValue(): Boolean?

  /**
   * @return value of `eq` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun eqValue(): Boolean?

  /**
   * @return value of `order` (std) or `cmp` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun orderValue(): Boolean?

  /**
   * @return value of `unsafe_hash` (std) or `hash` (attrs) parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun unsafeHashValue(): Boolean?

  /**
   * @return value of `frozen` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun frozenValue(): Boolean?

  /**
   * @return value of `matchArgs` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun matchArgsValue(): Boolean?

  /**
   * @return value of `kw_only` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun kwOnly(): Boolean?

  /**
   * @return value of `slots` parameter or
   * its default value if it is not specified or could not be evaluated.
   */
  fun slotsValue(): Boolean?

  /**
   * Opaque per-class payload written by the framework that built this stub, or `null` when it persisted nothing.
   * For built-in dataclass/attrs/transform classes this is always `null`. Decode it with [PyDataclassMetadata.decode],
   * after checking [type] names your framework.
   */
  val metadata: PyDataclassMetadata?
}
