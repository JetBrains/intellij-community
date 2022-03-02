package com.intellij.ide.starter.ide

interface CodeInjector {
  fun setup(context: IDETestContext)
  fun tearDown(context: IDETestContext)
}

interface PlainCodeInjector : CodeInjector {
  fun addGroovyCode(code: String)
}
