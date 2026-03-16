// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.plugins.terminal.session.ShellName
import java.nio.file.Path

internal class ShellIntegrationConfigurerImpl(
  override val shellName: ShellName,
  private val mutableEnvs: MutableMap<String, String>,
  private val translator: TerminalLocalPathTranslator,
  private val requester: Class<out ShellExecOptionsCustomizer>
) : ShellIntegrationConfigurer {

  override fun sourceShellScriptAtShellStartup(shellScriptFile: Path, args: List<String>) {
    val remotePath = translator.translateAbsoluteLocalPathToRemote(shellScriptFile) ?: run {
      LOG.info("$requester: sourceShellScriptFileAtShellStartup('$shellScriptFile') failed")
      return
    }
    // TODO Improve sourcing custom shell scripts (IJPL-234044)
    val prevValue = mutableEnvs.put(JEDITERM_SOURCE, remotePath.toString())
    if (prevValue != null) {
      LOG.info("$requester: '$JEDITERM_SOURCE' was overwritten from `$prevValue` to '$remotePath'")
    }
    else {
      LOG.debug { "$requester: sourceShellScriptFileAtShellStartup('$shellScriptFile', $args)" }
    }
    if (args.isNotEmpty()) {
      if (args.size == 1 && (shellName == ShellName.ZSH || shellName == ShellName.BASH)) {
        mutableEnvs[JEDITERM_SOURCE_SINGLE_ARG] = "1"
      }
      mutableEnvs[JEDITERM_SOURCE_ARGS] = args.joinToString(separator = " ")
    }
  }

  companion object {
    private val LOG: Logger = logger<ShellIntegrationConfigurerImpl>()
    private const val JEDITERM_SOURCE: String = "JEDITERM_SOURCE"
    private const val JEDITERM_SOURCE_SINGLE_ARG: String = "JEDITERM_SOURCE_SINGLE_ARG"
    private const val JEDITERM_SOURCE_ARGS: String = "JEDITERM_SOURCE_ARGS"
  }
}
