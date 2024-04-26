// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.process.ProcessOutputTypes
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.core.KotlinPositionManagerFactory
import org.jetbrains.kotlin.idea.debugger.core.KotlinSourcePositionHighlighter
import org.junit.Assert

abstract class AbstractIrBreakpointHighlightingTest : AbstractIrKotlinSteppingTest() {
  override fun extraPrintContext(context: SuspendContextImpl) {
    val positionManager = createPositionManager(context.debugProcess)
    val position = positionManager.getSourcePosition(context.location)
    val highlightRange = KotlinSourcePositionHighlighter().getHighlightRange(position)

    if (highlightRange == null) {
      println("Highlight whole line", ProcessOutputTypes.SYSTEM)
    }
    else {
      val lambdaText = position!!.file.text.substring(highlightRange.startOffset, highlightRange.endOffset)
      println("Highlight lambda range: '$lambdaText'", ProcessOutputTypes.SYSTEM)
    }
  }

  private fun createPositionManager(process: DebugProcess): KotlinPositionManager {
    val positionManager = KotlinPositionManagerFactory().createPositionManager(process) as KotlinPositionManager
    Assert.assertNotNull(positionManager)
    return positionManager
  }
}

abstract class AbstractK1IdeK2CodeBreakpointHighlightingTest : AbstractIrBreakpointHighlightingTest() {
  override val compileWithK2 = true

  override fun lambdasGenerationScheme() = JvmClosureGenerationScheme.INDY
}
