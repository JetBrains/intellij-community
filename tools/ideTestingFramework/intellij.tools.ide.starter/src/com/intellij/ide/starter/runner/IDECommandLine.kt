// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.runner

sealed class IDECommandLine {
  data class Args(val args: List<String>) : IDECommandLine()
  object OpenTestCaseProject : IDECommandLine()
}

fun args(vararg args: String) = IDECommandLine.Args(args.toList())
fun args(args: List<String>) = IDECommandLine.Args(args.toList())
operator fun IDECommandLine.Args.plus(params: List<String>) = copy(args = this.args + params)