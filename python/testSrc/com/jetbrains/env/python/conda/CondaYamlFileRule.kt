// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.util.io.delete
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Path

/**
 */
internal class CondaYamlFileRule(private val condaRule: LocalCondaRule,
                                 private val languageLevel: LanguageLevel = LanguageLevel.PYTHON39) : ExternalResource() {

  lateinit var yamlFilePath: Path
    private set

  val envName: String = Math.random().toString()

  override fun before() {
    val fullPathOnTarget = condaRule.condaPathOnTarget
    val command = PyCondaCommand(fullPathOnTarget, null, null)

    val condaEnvRequest = NewCondaEnvRequest.EmptyNamedEnv(languageLevel, envName)
    val env = PyCondaEnvIdentity.NamedEnv(condaEnvRequest.envName)
    val yamlFileText = runBlocking {
      PyCondaEnv.createEnv(command, condaEnvRequest).getOrThrow()
      CondaExecutor.exportEnvironmentFile(command.asBinaryToExec(), env)
    }.getOrThrow()

    val file = File.createTempFile("ijconda", ".yaml")
    file.writeText(yamlFileText)
    yamlFilePath = file.toPath()
  }

  override fun after() {
    yamlFilePath.delete()
  }
}