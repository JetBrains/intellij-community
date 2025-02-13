// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.jetbrains.python.PythonBinary
import java.nio.file.Path

/**
 * Mark [PythonBinary] param with [PythonBinary] annotation and register this extension with [com.intellij.python.junit5Tests.framework.env.PyEnvTestCase].
 *
 * It also searches for poetry and stores it in [PropertiesComponent]
 */
internal class VanillaPythonEnvExtension : PythonEnvExtensionBase<PythonBinary, TypeVanillaPython3>(
  annotation = PythonBinaryPath::class,
  pythonType = TypeVanillaPython3,
  envType = PythonBinary::class,
  lazy = false,
  additionalTags = arrayOf("poetry")
) {
  private companion object {
    val checkedPoetries = mutableMapOf<Path, Unit>()

    private fun resolveToolBinary(env: PythonBinary, name: String) =
      if (SystemInfoRt.isWindows) {
        env.parent.resolveSibling("Script/${name}.exe")
      }
      else {
        env.resolveSibling(name)
      }
  }

  override fun onEnvFound(env: PythonBinary) {
    val poetry = resolveToolBinary(env, "poetry")
    if (poetry !in checkedPoetries) {
      val output = CapturingProcessHandler(GeneralCommandLine(poetry.toString(), "--version")).runProcess(60_000, true)
      assert(output.exitCode == 0) { "$poetry seems to be broken, output: $output. For Windows check `fix_path.cmd`" }
      LOG.info("Poetry found at $poetry")
      checkedPoetries[poetry] = Unit
    }
    // There is no API that accepts path to poetry: only this global object is used
    PropertiesComponent.getInstance().poetryPath = poetry.toString()

    // TODO: find a way to extract this logic into one place to provide a uniform way to resolve python tool binaries
    val uv = resolveToolBinary(env, "uv")
    PropertiesComponent.getInstance().setValue(
      "PyCharm.Uv.Path",
      uv.toString()
    )
  }
}