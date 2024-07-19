// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

/**
 * Just collects all strings by concatenating them in [StringBuilder].
 */
internal class SimpleStringCollector : StringCollector {
  private val output: StringBuilder = StringBuilder()

  override fun write(text: String) {
    output.append(text)
  }

  override fun length(): Int {
    return output.length
  }

  override fun buildText(): String {
    return output.toString()
  }
}