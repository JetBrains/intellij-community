// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.black.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.xmlb.XmlSerializerUtil
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.pythonSdk
import java.util.*

const val BLACK_ID: String = "Black"

@State(name = BLACK_ID)
data class BlackFormatterConfiguration(var enabledOnReformat: Boolean,
                                       var enabledOnSave: Boolean,
                                       var executionMode: ExecutionMode,
                                       var pathToExecutable: String?,
                                       var cmdArguments: String,
                                       var sdkUUID: String?)
  : PersistentStateComponent<BlackFormatterConfiguration> {

  @Suppress("unused") // Empty constructor required for state components
  constructor() : this(false,
                       false,
                       ExecutionMode.PACKAGE,
                       null,
                       "",
                       null)

  enum class ExecutionMode {
    BINARY,
    PACKAGE,
  }

  fun getSdk(project: Project): Sdk? = sdkUUID?.let { uuidString ->
    val uuid = runCatching {
      UUID.fromString(uuidString)
    }.getOrElse {
      return@let null
    }

    project.modules
      .mapNotNull { it.pythonSdk }
      .firstOrNull { sdk -> (sdk.sdkAdditionalData as PythonSdkAdditionalData).uuid == uuid }
  }

  companion object {

    val options = listOf(
      BlackFormatterOption(listOf("-l", "--line-length"), "<length>", "How many characters per line to allow. [default: 88]"),
      BlackFormatterOption(listOf("-x", "--skip-source-first-line"), null, "Skip the first line of the source code"),
      BlackFormatterOption(listOf("-S", "--skip-string-normalization"), null, "Don't normalize string quotes or prefixes"),
      BlackFormatterOption(listOf("-C", "--skip-magic-trailing-comma"), null, "Don't use trailing commas as a reason to split lines"),
      BlackFormatterOption(listOf("--fast", "--safe"), null, "Skip temporary sanity checks [default: --safe]"),
      BlackFormatterOption(listOf("--config"), "FILE", "Read configuration from FILE path."),
      BlackFormatterOption(listOf("--preview"), null, "Enable potentially disruptive style changes\n" +
                                                      "that may be added to Black's main\n" +
                                                      "functionality in the next major release."),
      BlackFormatterOption(listOf("-t", "--target-version"), "[ver1, ver2..]", "Python versions that should be supported by\n" +
                                                                               "Black's output. By default, Black will try\n" +
                                                                               "to infer this from the project metadata in\n" +
                                                                               "pyproject.toml. If this does not yield\n" +
                                                                               "conclusive results, Black will use per-file\n" +
                                                                               "auto-detection."),
    )

    fun getBlackConfiguration(project: Project): BlackFormatterConfiguration = project.getService(BlackFormatterConfiguration::class.java)
  }

  class CliOptionFlag(val flag: String, val option: BlackFormatterOption) {
    internal fun description(): String {
      if (isPrimaryFlag(flag)) {
        return option.description
      }
      val primaryFlag = option.flags.find(::isPrimaryFlag)
      return if (primaryFlag != null) "See $primaryFlag" else option.description
    }

    private fun isPrimaryFlag(flag: String): Boolean = flag.startsWith("--")
  }

  data class BlackFormatterOption(val flags: List<String>, val param: String?, val description: String) {

    companion object {
      fun List<BlackFormatterOption>.toCliOptionFlags() = this.flatMap { option ->
        option.flags.map { CliOptionFlag(it, option) }
      }
    }
  }

  override fun getState(): BlackFormatterConfiguration = this

  override fun loadState(state: BlackFormatterConfiguration) = XmlSerializerUtil.copyBean(state, this)
}