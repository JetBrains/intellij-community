// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing.pyTestFixtures


const val CONFTEST_PY = "conftest.py"
const val REQUEST_FIXTURE = "request"
const val _PYTEST_DIR = "_pytest"
const val USE_FIXTURES = "usefixtures"
const val PARAMETRIZE = "parametrize"

val reservedFixturesSet = setOf(
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

val reservedFixtureClassSet = setOf(
  "testdir",
  "tmpdir",
  "tmpdir_factory"
)