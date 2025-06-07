// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.util.PropertiesComponent
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.python.community.testFramework.testEnv.PythonType
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.resolvePythonTool
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.resolvePythonHome
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
  }

  override fun onEnvFound(env: PythonBinary) {
    val poetry = env.resolvePythonHome().resolvePythonTool("poetry")
    if (poetry !in checkedPoetries) {
      val output = try {
        CapturingProcessHandler(GeneralCommandLine(poetry.toString(), "--version")).runProcess(60_000, true)
      }
      catch (e: ProcessNotCreatedException) {
        val customPythonMessage = buildString {
          PythonType.customPythonMessage?.let {
            append(it)
            append(" install poetry there, i.e: 'python -m pip install poetry' ")
          }
          append(" or run/rerun ")
          append(PythonType.BUILD_KTS_MESSAGE)
        }
        throw AssertionError(customPythonMessage, e)
      }
      assert(output.exitCode == 0) { "$poetry seems to be broken, output: $output. For Windows check `fix_path.cmd`" }
      LOG.info("Poetry found at $poetry")
      checkedPoetries[poetry] = Unit
    }
    // There is no API that accepts path to poetry: only this global object is used
    PropertiesComponent.getInstance().poetryPath = poetry.toString()

    val uv = env.resolvePythonHome().resolvePythonTool("uv")
    PropertiesComponent.getInstance().setValue(
      "PyCharm.Uv.Path",
      uv.toString()
    )
  }
}