// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.black.configuration

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlin.text.trimIndent

const val BLACK_ID: String = "Black"

@Service(Service.Level.PROJECT)
@State(name = BLACK_ID)
data class BlackFormatterConfiguration(
  @Deprecated("replaced with PyToolState") var enabledOnReformat: Boolean,
  @Deprecated("replaced with PyToolState") var enabledOnSave: Boolean,
  @Deprecated("replaced with PyToolState") var executionMode: ExecutionMode,
  @Deprecated("replaced with PyToolState") var pathToExecutable: String?,
  var cmdArguments: String,
  @Deprecated("replaced with PyToolState") var sdkName: String?,
) : PersistentStateComponent<BlackFormatterConfiguration> {

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

  companion object {

    val options: List<BlackFormatterOption> = listOf(
      BlackFormatterOption(listOf("-l", "--line-length"), "<length>", "How many characters per line to allow. [default: 88]"),
      BlackFormatterOption(listOf("-x", "--skip-source-first-line"), null, "Skip the first line of the source code"),
      BlackFormatterOption(listOf("-S", "--skip-string-normalization"), null, "Don't normalize string quotes or prefixes"),
      BlackFormatterOption(listOf("-C", "--skip-magic-trailing-comma"), null, "Don't use trailing commas as a reason to split lines"),
      BlackFormatterOption(listOf("--fast", "--safe"), null, "Skip temporary sanity checks [default: --safe]"),
      BlackFormatterOption(listOf("--config"), "FILE", "Read configuration from FILE path."),
      BlackFormatterOption(listOf("--preview"), null,
                           """
                             Enable potentially disruptive style changes
                             that may be added to Black's main
                             functionality in the next major release.
                             """.trimIndent()
      ),
      BlackFormatterOption(listOf("-t", "--target-version"), "[ver1, ver2..]",
                           """
                             Python versions that should be supported by
                             Black's output. By default, Black will try
                             to infer this from the project metadata in
                             pyproject.toml. If this does not yield
                             conclusive results, Black will use per-file
                             auto-detection.
                             """.trimIndent()
      ),
    )

    fun getBlackConfiguration(project: Project): BlackFormatterConfiguration {
      return project.getService(BlackFormatterConfiguration::class.java)
    }
  }

  class CliOptionFlag(val flag: String, val option: BlackFormatterOption) {
    fun description(): String {
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
      fun List<BlackFormatterOption>.toCliOptionFlags(): List<CliOptionFlag> = this.flatMap { option ->
        option.flags.map { CliOptionFlag(it, option) }
      }
    }
  }

  override fun getState(): BlackFormatterConfiguration = this

  override fun loadState(state: BlackFormatterConfiguration): Unit = XmlSerializerUtil.copyBean(state, this)
}