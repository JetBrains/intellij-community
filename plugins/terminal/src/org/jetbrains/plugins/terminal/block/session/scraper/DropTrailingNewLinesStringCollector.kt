// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

/**
 * Decorator over other [StringCollector].
 */
internal class DropTrailingNewLinesStringCollector(
  private val delegate: StringCollector,
) : StringCollector {
  private var pendingNewLines: Int = 0

  override fun write(text: String) {
    if (!text.isEmpty()) {
      repeat(pendingNewLines) {
        delegate.newline()
      }
      pendingNewLines = 0
    }
    delegate.write(text)
  }

  override fun newline() {
    pendingNewLines++
  }

  override fun buildText(): String {
    return delegate.buildText()
  }

  override fun length(): Int {
    return delegate.length()
  }
}