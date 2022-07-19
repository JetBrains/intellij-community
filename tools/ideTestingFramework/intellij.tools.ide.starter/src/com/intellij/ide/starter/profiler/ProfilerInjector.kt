// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.profiler

import com.intellij.ide.starter.runner.IDERunContext

abstract class ProfilerInjector(val type: ProfilerType) {
  abstract fun injectProfiler(runContext: IDERunContext): IDERunContext
}