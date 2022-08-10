package com.intellij.ide.starter.runner

sealed class IDECommandLine {
  data class Args(val args: List<String>) : IDECommandLine()
  object OpenTestCaseProject : IDECommandLine()
}

fun args(vararg args: String) = IDECommandLine.Args(args.toList())
fun args(args: List<String>) = IDECommandLine.Args(args.toList())
operator fun IDECommandLine.Args.plus(params: List<String>) = copy(args = this.args + params)