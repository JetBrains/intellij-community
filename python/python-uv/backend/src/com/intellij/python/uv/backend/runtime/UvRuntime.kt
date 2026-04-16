// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.uv.backend.runtime

import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.ExecOptions
import com.intellij.python.pytools.runtime.PyToolRuntime
import com.intellij.python.uv.backend.cli.uv.UvCli
import com.intellij.python.uv.backend.cli.uvx.UvxCli
import java.nio.file.Path

@Suppress("unused")
fun PyToolRuntime.uvCli(): UvCli = UvCli(this)

@Suppress("unused")
fun PyToolRuntime.uvxCli(): UvxCli = UvxCli(this)

/**
 * Build a [PyToolRuntime] whose binary is the given local `uv` executable.
 *
 * Callers are expected to resolve [uvExecutable] themselves (the canonical resolver is
 * `getUvExecutableLocal` in `intellij.python.community.impl`); this helper only encapsulates the
 * `BinOnEel` / `ExecOptions` shape so `runtime.uvCli()` / `runtime.uvxCli()` usage stays uniform
 * across call sites.
 */
fun createUvToolRuntime(uvExecutable: Path): PyToolRuntime =
  PyToolRuntime(binary = BinOnEel(uvExecutable), execOptions = ExecOptions())
