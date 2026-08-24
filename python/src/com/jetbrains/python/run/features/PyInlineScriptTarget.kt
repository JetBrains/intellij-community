// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.features

import com.intellij.openapi.util.io.toNioPathOrNull
import com.jetbrains.python.run.PythonRunConfiguration
import com.jetbrains.python.run.PythonScriptCommandLineState
import java.nio.file.Path

/**
 * The script [configuration] should be run as, in the sense of PEP 723, or `null` when it should be run the ordinary
 * way.
 *
 * `runAsScript` decides when it is set, and the presence of a metadata block decides otherwise, so a block added to a
 * script later takes effect without touching the configuration.
 *
 * Says nothing about *how* such a script is run — that is up to whichever [PyRunToolProvider] handles the SDK.
 */
internal fun resolveInlineScriptTarget(configuration: PythonRunConfiguration): Path? {
  // getExpandedScriptName is declared not-null but expands a name that may not be set yet, so check before expanding.
  if (configuration.scriptName.isNullOrBlank()) return null
  val script = PythonScriptCommandLineState.getExpandedScriptName(configuration).toNioPathOrNull() ?: return null

  return when (configuration.runAsScript) {
    false -> null
    true -> script
    // Auto: only a script that carries inline metadata has anything for the tool to install.
    null -> script.takeIf { hasInlineScriptMetadata(it) }
  }
}
