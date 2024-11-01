// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.LibrarySupportProvider
import com.intellij.debugger.streams.trace.CollectionTreeBuilder
import com.intellij.debugger.streams.trace.XValueInterpreter
import com.intellij.debugger.streams.trace.impl.JavaValueInterpreter
import com.intellij.debugger.streams.ui.impl.JavaCollectionTreeBuilder

abstract class JvmLibrarySupportProvider : LibrarySupportProvider {

  companion object {
    private val interpreter : XValueInterpreter by lazy { JavaValueInterpreter() }
    private val treeBuilder : CollectionTreeBuilder by lazy { JavaCollectionTreeBuilder() }
  }

  override fun getXValueInterpreter(): XValueInterpreter = interpreter

  override fun getCollectionTreeBuilder(): CollectionTreeBuilder = treeBuilder
}
