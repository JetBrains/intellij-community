// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.core.lib.LibrarySupportProvider
import com.intellij.debugger.streams.core.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.core.trace.XValueInterpreter
import com.intellij.debugger.streams.trace.impl.JavaValueInterpreter
import com.intellij.debugger.streams.ui.impl.JavaCollectionTreeBuilder
import com.intellij.openapi.project.Project

abstract class JvmLibrarySupportProvider : LibrarySupportProvider {
  companion object {
    private val interpreter : XValueInterpreter by lazy { JavaValueInterpreter() }
  }

  override fun getXValueInterpreter(project: Project): XValueInterpreter = interpreter

  override fun getCollectionTreeBuilder(project: Project): CollectionTreeBuilder = JavaCollectionTreeBuilder(project)
}
