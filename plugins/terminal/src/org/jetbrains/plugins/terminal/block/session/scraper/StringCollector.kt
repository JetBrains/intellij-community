// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.session.scraper

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.block.session.NEW_LINE_STRING

@ApiStatus.Internal
interface StringCollector {
  fun buildText(): String
  fun write(text: String)
  fun newline(): Unit = write(NEW_LINE_STRING)
  fun length(): Int
}

