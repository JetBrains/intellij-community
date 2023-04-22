// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.exp

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jediterm.core.util.TermSize
import java.util.concurrent.CompletableFuture

interface TerminalCompletionProvider {
  fun getCompletionItems(command: String): CompletableFuture<List<String>>
}

class NewTerminalSessionCompletionProvider(private val project: Project) : TerminalCompletionProvider {
  override fun getCompletionItems(command: String): CompletableFuture<List<String>> {
    val maxLineLength = command.split('\n').maxOf { line -> line.length }
    val session = HeadlessTerminalSession(project, TermSize(maxLineLength + 100, 50))
    session.start()
    val itemsFuture = session.invokeCompletion(command)
    return itemsFuture.thenApply { items ->
      Disposer.dispose(session)
      items
    }
  }
}