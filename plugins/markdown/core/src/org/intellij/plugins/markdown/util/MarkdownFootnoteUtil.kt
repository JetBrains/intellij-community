// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.util

import org.jetbrains.annotations.ApiStatus

/**
 * Returns `true` if [text] is a well-formed footnote label bracket (e.g. `[^note]`).
 * The minimum valid form is `[^x]` (length 4).
 */
@ApiStatus.Internal
fun isFootnoteLabelText(text: String): Boolean =
  text.length > 3 && text.startsWith("[^") && text.endsWith("]") && !text.contains(Regex("[ \t]"))
