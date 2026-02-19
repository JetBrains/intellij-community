// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService

import org.jetbrains.annotations.Nls

internal sealed interface ExecOptionsBase {
  val env: Map<String, String>
  val processDescription: @Nls String?
  val tty: TtySize?
}