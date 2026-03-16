package com.intellij.ide.starter.runner

import com.intellij.ide.starter.models.IDEStartResult

/**
 * Represents a IDE process that can be executed within an IDE context.
 */
interface IDEProcess {
  suspend fun run(runContext: IDERunContext): IDEStartResult
}
