// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.reworked.util

import com.intellij.openapi.project.Project
import com.intellij.util.io.write
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer
import java.nio.file.Files

class ZshPS1Customizer(val ps1Suffix: String) : LocalTerminalCustomizer() {

  override fun customizeCommandAndEnvironment(project: Project, workingDirectory: String?, command: Array<out String>?, envs: MutableMap<String?, String?>): Array<out String?>? {
    val resource = ZshPS1Customizer::class.java.getResourceAsStream("ZshPS1Customizer-script.zsh")!!
    val file = Files.createTempFile(ZshPS1Customizer::class.java.simpleName, ".zsh")
    file.write(resource.readAllBytes())
    envs["INTELLIJ_PS1_SUFFIX"] = ps1Suffix
    envs["JEDITERM_SOURCE"] = file.toString()
    return command
  }
}
