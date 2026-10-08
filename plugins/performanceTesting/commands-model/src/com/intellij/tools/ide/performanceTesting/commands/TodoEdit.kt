// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands

/** An addition, a change, or a removal for the `%todoEdit` command. */
enum class TodoEdit(val argument: String) {
  ADD("add"),
  CHANGE("change"),
  REMOVE("remove"),
}
