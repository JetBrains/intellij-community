package com.intellij.ide.starter.profiler

import com.intellij.ide.starter.runner.IDERunContext

abstract class ProfilerInjector(val type: ProfilerType) {
  abstract fun injectProfiler(runContext: IDERunContext): IDERunContext
}