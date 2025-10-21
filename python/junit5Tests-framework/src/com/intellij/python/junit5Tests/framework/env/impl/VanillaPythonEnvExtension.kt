// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.util.PropertiesComponent
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.python.community.testFramework.testEnv.PythonType
import com.intellij.python.community.testFramework.testEnv.TypeVanillaPython3
import com.intellij.python.junit5Tests.framework.env.PythonBinaryPath
import com.intellij.python.junit5Tests.framework.resolvePythonTool
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.sdk.impl.resolvePythonHome
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
    val checkedTools = mutableMapOf<String, MutableSet<Path>>()
  }

  override fun onEnvFound(env: PythonBinary) {
    // There is no API that accepts path to poetry or pipenv: only this global object is used
    PropertiesComponent.getInstance().poetryPath = checkAndGetToolPath(env, "poetry", true)
    PropertiesComponent.getInstance().pipenvPath = checkAndGetToolPath(env, "pipenv", false)

    val uv = env.resolvePythonHome().resolvePythonTool("uv")
    PropertiesComponent.getInstance().setValue(
      "PyCharm.Uv.Path",
      uv.toString()
    )
  }

  private fun checkAndGetToolPath(env: PythonBinary, toolName: String, toThrow: Boolean): String? {
    val tool = env.resolvePythonHome().resolvePythonTool(toolName)
    if (checkedTools[toolName]?.contains(tool) != true) {
      val output = try {
        CapturingProcessHandler(GeneralCommandLine(tool.toString(), "--version")).runProcess(60_000, true)
      }
      catch (e: ProcessNotCreatedException) {
        val customPythonMessage = buildString {
          PythonType.customPythonMessage?.let {
            append(it)
            append(" install ${toolName} there, i.e: 'python -m pip install ${toolName}' ")
          }
          append(" or run/rerun ")
          append(PythonType.BUILD_KTS_MESSAGE)
        }
        if (toThrow) {
          throw AssertionError(customPythonMessage, e)
        }
        else {
          LOG.error(customPythonMessage)
          return null
        }
      }
      assert(output.exitCode == 0) { "$tool seems to be broken, output: $output. For Windows check `fix_path.cmd`" }
      LOG.info("${toolName} found at $tool")
      checkedTools.compute(toolName) { _, v -> (v ?: mutableSetOf()).also { it.add(tool) } }
    }

    return tool.toString()
  }
}