// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.filtering

import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition
import com.sun.jdi.Method

/**
 * Result of the [classifyBySourcePosition] fast path.
 * - [traceable] - the chain has not started yet, keep it
 * - [notTraceable] - the chain has already (fully) executed, filter it out
 * - [uncertain] - the chain crosses the breakpoint line, only the precise bytecode analysis can decide
 */
private data class ClassifiedStreams(
  val traceable: List<StreamChain>,
  val notTraceable: List<StreamChain>,
  val uncertain: List<StreamChain>,
)

internal suspend fun filterTraceableStreams(
  project: Project,
  chains: List<StreamChain>,
  position: XSourcePosition,
  method: Method,
  bytecodeOffset: Long,
): List<StreamChain> {
  if (chains.isEmpty()) return chains
  val classified = readAction {
    val document = FileDocumentManager.getInstance().getDocument(position.file) ?: return@readAction null
    classifyBySourcePosition(chains, document, position.line)
  } ?: return chains
  return classified.traceable + classified.uncertain
}

private fun classifyBySourcePosition(
  chains: List<StreamChain>,
  document: Document,
  currentLine: Int,
): ClassifiedStreams {
  val traceable = mutableListOf<StreamChain>()
  val notTraceable = mutableListOf<StreamChain>()
  val uncertain = mutableListOf<StreamChain>()
  for (chain in chains) {
    val startLine = document.getLineNumber(chain.qualifierExpression.textRange.startOffset)
    val endLine = document.getLineNumber(chain.terminationCall.textRange.endOffset)
    when {
      endLine < currentLine -> notTraceable += chain
      startLine > currentLine -> traceable += chain
      else -> uncertain += chain
    }
  }
  return ClassifiedStreams(traceable, notTraceable, uncertain)
}
