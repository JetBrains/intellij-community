// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.PyBuiltinTypeTest
import com.jetbrains.python.PyCallableTypeTest
import com.jetbrains.python.PyCollectionTypeTest
import com.jetbrains.python.PyComprehensionAndIteratorTypeTest
import com.jetbrains.python.PyDataclassTypeTest
import com.jetbrains.python.PyEnumTypeTest
import com.jetbrains.python.PyExpectedVarianceJudgmentTest
import com.jetbrains.python.PyGenericTypeTest
import com.jetbrains.python.PyInferenceMiscTypeTest
import com.jetbrains.python.PyInferredVarianceJudgmentTest
import com.jetbrains.python.PyLiteralTypeTest
import com.jetbrains.python.PyNamedTupleTypeTest
import com.jetbrains.python.PyNarrowingTypeTest
import com.jetbrains.python.PyNewTypeTypeTest
import com.jetbrains.python.PyOverloadTypeTest
import com.jetbrains.python.PyProtocolTypeTest
import com.jetbrains.python.PySubtypingTypeTest
import com.jetbrains.python.PyTupleTypeTest
import com.jetbrains.python.PyTypeAliasAndDefaultsTest
import com.jetbrains.python.PyTypeAliasAndFormsTest
import com.jetbrains.python.PyTypeCommentTypeTest
import com.jetbrains.python.PyTypedDictTypeTest
import com.jetbrains.python.PyUnionTypeTest
import com.jetbrains.python.PyVariadicGenericTypeTest
import com.jetbrains.python.PyVarianceTest
import com.jetbrains.python.pyi.PyiTypeTest
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite

/**
 * Aggregating suite for pure Python typing tests.
 */
@Suite
@SelectClasses(
  PyEnumTypeTest::class,
  PyLiteralTypeTest::class,
  PyTypedDictTypeTest::class,
  PyOverloadTypeTest::class,
  PyGenericTypeTest::class,
  PyNarrowingTypeTest::class,
  PyProtocolTypeTest::class,
  PyCallableTypeTest::class,
  PyDataclassTypeTest::class,
  PyNamedTupleTypeTest::class,
  PyNewTypeTypeTest::class,
  PyTupleTypeTest::class,
  PyUnionTypeTest::class,
  PyComprehensionAndIteratorTypeTest::class,
  PyAttributeAndDescriptorTypeTest::class,
  PyBuiltinTypeTest::class,
  PyTypeAliasAndFormsTest::class,
  PyVariadicGenericTypeTest::class,
  PyTypeAliasAndDefaultsTest::class,
  PySubtypingTypeTest::class,
  PyCollectionTypeTest::class,
  PyTypeCommentTypeTest::class,
  PyInferenceMiscTypeTest::class,
  PyiTypeTest::class,
  PyVarianceTest::class,
  PyExpectedVarianceJudgmentTest::class,
  PyInferredVarianceJudgmentTest::class,
)
class PyPureTypingTestSuite