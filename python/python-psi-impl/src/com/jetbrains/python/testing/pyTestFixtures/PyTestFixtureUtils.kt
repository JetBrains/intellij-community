// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures

import com.jetbrains.python.psi.PyDecorator
import com.jetbrains.python.psi.impl.PyEvaluator
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal const val CONFTEST_PY = "conftest.py"
@ApiStatus.Internal const val REQUEST_FIXTURE = "request"
@ApiStatus.Internal const val _PYTEST_DIR = "_pytest"
@ApiStatus.Internal const val USE_FIXTURES = "usefixtures"
@ApiStatus.Internal const val PARAMETRIZE = "parametrize"

@ApiStatus.Internal val reservedFixturesSet = setOf(
  "capfd",
  "capfdbinary",
  "caplog",
  "capsys",
  "capsysbinary",
  "cache",
  "doctest_namespace",
  "monkeypatch",
  "pytestconfig",
  "pytester",
  "record_property",
  "record_testsuite_property",
  "recwarn",
  "tmp_path",
  "tmp_path_factory"
)

@ApiStatus.Internal val reservedFixtureClassSet = setOf(
  "testdir",
  "tmpdir",
  "tmpdir_factory"
)

@ApiStatus.Internal
val TEST_FIXTURE_DECORATOR_NAMES = listOf("pytest.fixture", "fixture", "pytest_asyncio.fixture")

@ApiStatus.Internal
fun getTestFixtureName(decorator: PyDecorator): String? {
  val nameArgument = decorator.getKeywordArgument("name") ?: return null
  return PyEvaluator.evaluateNoResolve(nameArgument, String::class.java)
}
