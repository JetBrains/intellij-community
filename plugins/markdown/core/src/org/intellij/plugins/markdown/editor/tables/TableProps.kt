// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.editor.tables

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TableProps {
  const val SEPARATOR_CHAR: Char = '|'
  const val MIN_CELL_WIDTH: Int = 5
  const val CARET_REPLACE_CHAR: Char = '\n'
}
