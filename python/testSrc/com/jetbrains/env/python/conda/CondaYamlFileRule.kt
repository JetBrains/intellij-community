// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.python.conda

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.intellij.python.junit5Tests.framework.winLockedFile.deleteCheckLocking
import com.intellij.util.io.delete
import com.jetbrains.python.getOrThrow
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.execution.CondaExecutor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.rules.ExternalResource
import java.io.File
import java.nio.file.Path

/**
 * Writes an environment file for the env [envName]. This env does not exist, so `conda env create -f` must create it.
 *
 * The rule exports a different env to get the file content, then writes [envName] into the file and removes the `prefix`.
 * Thus `conda env create -f` never sees an existing directory, even if the delete of the exported env did not remove it.
 */
internal class CondaYamlFileRule(
  private val condaRule: LocalCondaRule,
  private val languageLevel: LanguageLevel = LanguageLevel.PYTHON39,
) : ExternalResource() {

  lateinit var yamlFilePath: Path
    private set

  val envName: String = Math.random().toString()

  override fun before() {
    val command = PyCondaCommand(condaRule.condaPathOnTarget, null, null)

    val sourceEnvName = Math.random().toString()
    val sourceEnvRequest = NewCondaEnvRequest.EmptyNamedEnv(languageLevel, sourceEnvName)
    val exportedText = runBlocking {
      PyCondaEnv.createEnv(command, sourceEnvRequest).getOrThrow()
      CondaExecutor.exportEnvironmentFile(command.asBinaryToExec(), PyCondaEnvIdentity.NamedEnv(sourceEnvName))
    }.getOrThrow()
    (val prefix, val channels, val dependencies) = yaml.decodeFromString(CondaEnvYaml.serializer(), exportedText)
    checkNotNull(prefix) { "No prefix in the exported file: $exportedText" }

    val file = File.createTempFile("ijconda", ".yaml")
    file.writeText(yaml.encodeToString(CondaEnvYaml.serializer(),
                                       CondaEnvYaml(name = envName, channels = channels, dependencies = dependencies)))
    yamlFilePath = file.toPath()

    // Clean up the source env: the test does not need it.
    deleteCheckLocking(Path.of(prefix))
  }

  override fun after() {
    yamlFilePath.delete()
  }
}

/**
 * Does not write default values, so the file has no `prefix` when it is `null`.
 */
private val yaml = Yaml(configuration = YamlConfiguration(encodeDefaults = false))

/**
 * `conda env export` always writes [prefix]. Keep it `null` in a file for `conda env create`.
 */
@Serializable // TODO: Create KSerializer<Path> in the platform, use it instead of String for prefix
private data class CondaEnvYaml(
  val name: String,
  val prefix: String? = null,
  val channels: List<String>,
  val dependencies: List<String>,
)
