package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.poetry.poetryPath
import com.jetbrains.python.tools.PythonType
import java.nio.file.Path

/**
 * Mark [PythonBinary] param with [PythonBinary] annotation and register this extension with [com.intellij.python.junit5Tests.framework.env.PyEnvTestCase].
 *
 * It also searches for poetry and stores it in [PropertiesComponent]
 */
internal class VanillaPythonEnvExtension : PythonEnvExtensionBase<PythonBinary, PythonType.VanillaPython3>(
  annotation = PythonBinaryPath::class,
  pythonType = PythonType.VanillaPython3,
  envType = PythonBinary::class,
  lazy = false,
  additionalTags = arrayOf("poetry")
) {
  private companion object {
    val checkedPoetries = mutableMapOf<Path, Unit>()
  }

  override fun onEnvFound(env: PythonBinary) {
    val poetry = if (SystemInfoRt.isWindows) env.parent.resolve("Scripts/poetry.exe") else env.resolveSibling("poetry")
    if (poetry !in checkedPoetries) {
      val output = CapturingProcessHandler(GeneralCommandLine(poetry.toString(), "--version")).runProcess(60_000, true)
      assert(output.exitCode == 0) { "$poetry seems to be broken, output: $output. For Windows check `fix_path.cmd`" }
      LOG.info("Poetry found at $poetry")
      checkedPoetries[poetry] = Unit
    }
    // There is no API that accepts path to poetry: only this global object is used
    PropertiesComponent.getInstance().poetryPath = poetry.toString()
  }
}