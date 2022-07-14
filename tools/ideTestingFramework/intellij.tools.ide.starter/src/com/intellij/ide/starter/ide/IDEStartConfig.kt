package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptionsDiff
import java.nio.file.Path

interface IDEStartConfig {
  val workDir: Path

  val environmentVariables: Map<String, String>
    get() = System.getenv().filterKeys {
      // don't inherit these environment variables from parent process
      it != "IDEA_PROPERTIES" && !it.endsWith("VM_OPTIONS") && it != "JAVA_HOME" && !it.endsWith("_JDK")
    }

  val commandLine: List<String>

  fun vmOptionsDiff(): VMOptionsDiff? = null

  val errorDiagnosticFiles: List<Path>
    get() = emptyList()
}