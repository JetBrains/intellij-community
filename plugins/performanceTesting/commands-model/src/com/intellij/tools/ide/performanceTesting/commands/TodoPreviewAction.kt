// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands

/** The actions of the `%todoPreview` command. */
enum class TodoPreviewAction(val argument: String) {
  SELECT("select"),
  NEXT("next"),
  PREVIOUS("previous"),
}
