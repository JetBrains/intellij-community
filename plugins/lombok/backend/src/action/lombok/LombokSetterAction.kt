// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lombok.backend.action.lombok

import com.intellij.lombok.shared.LombokBundle
import com.intellij.openapi.actionSystem.AnActionEvent

class LombokSetterAction : BaseLombokAction(::LombokSetterModCommandAction) {
  override fun getActionText(e: AnActionEvent): String =
    LombokBundle.message("action.defaultLombokSetter.text")
}
