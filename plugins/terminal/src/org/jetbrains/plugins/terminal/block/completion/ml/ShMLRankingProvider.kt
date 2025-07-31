// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.block.completion.ml

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.TerminalBundle

@ApiStatus.Internal
class ShMLRankingProvider : CatBoostJarCompletionModelProvider(
  TerminalBundle.message("settings.shell.language"), "sh_features", "sh_model") {

  override fun isEnabledByDefault(): Boolean = true

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("shell script", ignoreCase = true) == 0
}
