package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext

const val FINGERPRINT_DEBUG_PROPERTY: String = "intellij.build.fingerprint.debug"
const val FINGERPRINT_DEBUG_FILE_NAME: String = "fingerprint-debug.txt"

sealed class IDECommandLine(open val args: List<String>) {
  data class Args(override val args: List<String>) : IDECommandLine(args), (IDERunContext) -> IDECommandLine {
    constructor(vararg args: String) : this(args.toList())

    operator fun plus(params: List<String>): Args = copy(args = this.args + params)

    override fun invoke(runContext: IDERunContext): IDECommandLine = this
  }

  data object StartIdeWithoutProject : IDECommandLine(listOf())

  data class OpenTestCaseProject(
    val testContext: IDETestContext,
    val additionalArgs: List<String> = emptyList(),
  ) : IDECommandLine(
    additionalArgs + listOf(testContext.resolvedProjectHome.toAbsolutePath().toString())
  )
}

fun openTestCaseProject(runContext: IDERunContext): IDECommandLine {
  return IDECommandLine.OpenTestCaseProject(runContext.testContext)
}

fun startIdeWithoutProject(runContext: IDERunContext): IDECommandLine {
  return IDECommandLine.StartIdeWithoutProject
}