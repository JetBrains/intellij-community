// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.util.io.delete
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
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

  val envName: String = "file_${Math.random()}.yaml"

  override fun before() {
    val fullPathOnTarget = condaRule.condaPathOnTarget
    val command = PyCondaCommand(fullPathOnTarget, null, null)
    val condaEnvRequest = NewCondaEnvRequest.EmptyNamedEnv(languageLevel, envName)
    runBlocking { PyCondaEnv.createEnv(command, condaEnvRequest).mapFlat { it.getResultStdoutStr() }.getOrThrow() }
    val targetReq = LocalTargetEnvironmentRequest()
    val builder = TargetedCommandLineBuilder(targetReq).apply {
      setExePath(fullPathOnTarget)
      addParameter("env")
      addParameter("export")
      addParameter("-n")
      addParameter(envName)
    }
    val targetEnv = targetReq.prepareEnvironment(TargetProgressIndicator.EMPTY)
    val yaml = targetEnv.createProcess(builder.build()).let { runBlocking { it.getResultStdoutStr() } }.getOrThrow()
    val file = File.createTempFile("ijconda", ".yaml")
    file.writeText(yaml)
    yamlFilePath = file.toPath()
  }

  override fun after() {
    yamlFilePath.delete()
  }
}