// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import org.jetbrains.annotations.ApiStatus

/**
 * One argument of a test-runner command line, with the path part kept apart from the runner syntax around it.
 *
 * A test specification joins a path and a test name into one argument: `tests/a.py::test_x`, `a.py::::doctest_fn`,
 * `folder/;pattern`. The builder of the argument knows where the path ends. A reader of the finished string does not,
 * and a reader that looks for the separator has to know every runner's syntax. A launcher that must convert the path,
 * such as the debugpy backend for a remote interpreter, therefore asks for the parts instead of the joined value.
 *
 * [asLocalString] rebuilds the joined value a local runner reads, so a caller that needs no conversion is unaffected.
 */
@ApiStatus.Internal
sealed interface PyTestSpecArg {
  /** The argument as a local runner reads it. */
  fun asLocalString(): String

  /** An argument that names no path: an option, a raw argument, or a python qualified name such as `pkg.mod.test_x`. */
  data class Plain(val value: String) : PyTestSpecArg {
    override fun asLocalString(): String = value
  }

  /**
   * An argument that starts with [path], followed by [suffix].
   *
   * [path] is the value the run configuration stores, so it can be relative to the working directory. [suffix] holds
   * the runner syntax, `::test_x` or `/;pattern` for example, and never a path.
   */
  data class WithPath(val path: String, val suffix: String = "") : PyTestSpecArg {
    override fun asLocalString(): String = path + suffix
  }
}

@ApiStatus.Internal
fun List<PyTestSpecArg>.asLocalStrings(): List<String> = map(PyTestSpecArg::asLocalString)
