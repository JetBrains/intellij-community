package com.intellij.ide.starter.runner

import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.ide.PlainCodeInjector
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

open class CodeBuilderHost : PlainCodeInjector {
  private var codeToRun: String? = null

  override fun addGroovyCode(code: String) {
    require(codeToRun == null) { "Only one code block is supported" }
    codeToRun = code
  }

  private val IDETestContext.targetFile
    get() = paths.configDir.resolve("extensions/com.intellij/startup/init.groovy")

  override fun setup(context: IDETestContext) {
    val code = codeToRun
    if (code != null) {
      context.targetFile.apply {
        parent.createDirectories()
        writeText(code)
      }
    }
  }

  override fun tearDown(context: IDETestContext) {
    context.targetFile.deleteIfExists()
  }
}