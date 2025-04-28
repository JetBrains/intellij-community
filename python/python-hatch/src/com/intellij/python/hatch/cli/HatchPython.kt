// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.hatch.cli

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.io.NioFiles
import com.intellij.python.hatch.cli.HatchPython.PythonInstallResponse.AbortReason
import com.intellij.python.hatch.runtime.HatchRuntime
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.ExecError
import java.nio.file.Path

/**
 * Hatch uses all available names if it finds the value "all" in the list. In this case it ignores other names specified.
 */
val ALL_NAMES: Array<String> = arrayOf("all")

/**
 * Manage environment dependencies
 */
class HatchPython(runtime: HatchRuntime) : HatchCommand("python", runtime) {
  /**
   * Locate Python binaries
   *
   * @param parent Show the parent directory of the Python binary
   * @param dir The directory in which distributions reside
   */
  suspend fun find(name: String, parent: Boolean? = null, dir: String? = null): Result<Path?, ExecError> {
    val options = listOf(parent to "--parent").makeOptions() + buildDirOption(dir)

    return executeAndHandleErrors("find", *options, name) { output ->
      when {
        output.exitCode == 1 && output.stderr.contains("Distribution not installed: $name") -> Result.success(null)
        output.exitCode != 0 -> Result.failure(null)
        else -> {
          val path = NioFiles.toPath(output.stdout.trim())
          path?.let { Result.success(it) } ?: Result.failure(null)
        }
      }
    }
  }


  /**
   * Used as a result for both install and update commands.
   * Hatch update command is an installation command with private and update flags set to true.
   * Install/Update operations are not transactional, and in case of the abort, the result might contain successfully installed/updated records.
   */
  data class PythonInstallResponse(
    val installed: Map<String, Path>,
    val updated: Map<String, Path>,
    val latestAlreadyInstalled: List<String>,
    val unparsedStdout: String,
    val abort: Abort?,
  ) {
    enum class AbortReason(val regex: Regex) {
      DISTRIBUTION_NOT_INSTALLED("""^Distributions not installed: (.*)$""".toRegex(RegexOption.MULTILINE)),
      UNKNOWN_DISTRIBUTIONS("""^Unknown distributions: (.*)$""".toRegex(RegexOption.MULTILINE)),
      INCOMPATIBLE_DISTRIBUTIONS("""^Incompatible distributions: (.*)$""".toRegex(RegexOption.MULTILINE)),
      DISTRIBUTION_ALREADY_INSTALLED("""^Distribution is already installed: (.*)$""".toRegex(RegexOption.MULTILINE));

      companion object {
        fun parse(stderr: String): AbortReason? = AbortReason.entries.firstOrNull { it.regex.containsMatchIn(stderr) }
      }
    }

    data class Abort(val reason: AbortReason, val stderr: String)
  }

  /**
   * Parsing of added paths is not implemented yet, but it will be stored in the unprocessedOutput anyway as a string.
   *
   * ```python
   * app.display(
   *     f'\nThe following director{"ies" if multiple else "y"} ha{"ve" if multiple else "s"} '
   *     f'been added to your PATH (pending a shell restart):\n'
   * )
   * for public_directory in directories_made_public:
   *     app.display(public_directory)
   * ```
   */
  fun parsePythonInstallCommandOutput(processOutput: ProcessOutput): PythonInstallResponse {
    val output = processOutput.stderr.replace("\r\n", "\n")

    val abort = AbortReason.parse(output)?.let { PythonInstallResponse.Abort(it, output) }

    val installRegex = Regex("""^Installing .+\nInstalled (.*) @ (.*)$""", RegexOption.MULTILINE)
    val installed = installRegex.findAll(output).associate {
      val (name, location) = it.destructured
      name to Path.of(location)
    }

    val updateRegex = Regex("""^Updating .+\nUpdated (.*) @ (.*)$""", RegexOption.MULTILINE)
    val updated = updateRegex.findAll(output).associate {
      val (name, location) = it.destructured
      name to Path.of(location)
    }

    val alreadyInstalledRegex = Regex("""^The latest version is already installed: (.*)$""", RegexOption.MULTILINE)
    val latestAlreadyInstalled = alreadyInstalledRegex.findAll(output).map {
      val (version) = it.destructured
      version
    }.toList()


    val unprocessedOutput = output
      .replace(installRegex, "")
      .replace(updateRegex, "")
      .replace(alreadyInstalledRegex, "")
      .let { if (abort != null) it.replace(abort.reason.regex, "") else it }

    return PythonInstallResponse(installed, updated, latestAlreadyInstalled, unprocessedOutput, abort)
  }


  /**
   * Install Python distributions.
   *
   * Operation is not transactional, and in case of the abort, the result might contain successfully installed/updated records.
   *
   * @param names Distributions to install, you may select `all` to install all compatible distributions
   * @param private Do not add distributions to the user PATH
   * @param update Update existing installations
   * @param dir  The directory in which to install distributions, overriding configuration
   */
  suspend fun install(
    vararg names: String = ALL_NAMES,
    private: Boolean? = null,
    update: Boolean? = null,
    dir: String? = null,
  ): Result<PythonInstallResponse, ExecError> {
    val options = listOf(update to "--update", private to "--private").makeOptions() + buildDirOption(dir)
    return executeAndHandleErrors("install", *options, *names) { output ->
      Result.success(parsePythonInstallCommandOutput(output))
    }
  }

  data class PythonRemoveResponse(val removed: List<String>, val notInstalled: List<String>)

  /**
   * Remove Python distributions
   *
   * @param names Distributions to remove, you may select `all` to install all compatible distributions
   * @param dir The directory in which distributions reside
   */
  suspend fun remove(vararg names: String = ALL_NAMES, dir: String? = null): Result<PythonRemoveResponse, ExecError> {
    return executeAndHandleErrors("remove", *buildDirOption(dir), *names) { processOutput ->
      val output = processOutput.stderr
      val notInstalledRegex = Regex("""^Distribution is not installed: (.*)$""", RegexOption.MULTILINE)
      val notInstalled = notInstalledRegex.findAll(output).map { it.destructured.component1() }.toList()

      val removedRegex = Regex("""^Removing (.*)$""", RegexOption.MULTILINE)
      val removed = removedRegex.findAll(output).map { it.destructured.component1() }.toList()

      Result.success(PythonRemoveResponse(removed, notInstalled))
    }
  }

  data class ShowResponse(val installed: Map<String, String>, val available: Map<String, String>)

  /**
   * Show the available Python distributions
   *
   * @param dir The directory in which distributions reside
   * @return Name to Version as a map
   */
  suspend fun show(dir: String? = null): Result<ShowResponse, ExecError> {
    val nameToVersionRegex = """\|\s+([^|\s]+)\s+\|\s+([^|\s]+)\s+\|""".toRegex()
    fun parseNameToVersions(payload: String) = nameToVersionRegex.findAll(payload).associate {
      val (name, version) = it.destructured
      name to version
    }

    val tableRegex = """
      \+-+\+-+\+
      \|\s*Name\s*\|\s*Version\s*\|
      \+=+\+=+\+
      ((?:\|\s+[^|\s]+\s+\|\s+[^|\s]+\s+\|
      \+-+\+-+\+\n)*)
      """.trimIndent().toRegex()

    val expectedOutput = """^(?:\s*Installed\s*\n$tableRegex)?\s*Available\s*\n$tableRegex$""".toRegex(RegexOption.MULTILINE)

    return executeAndMatch("show", "--ascii", *buildDirOption(dir), expectedOutput = expectedOutput) { matchResult ->
      matchResult.destructured.let { (installedTable, availableTable) ->
        ShowResponse(
          parseNameToVersions(installedTable),
          parseNameToVersions(availableTable)
        ).let { Result.success(it) }
      }
    }
  }

  /**
   * Update Python distributions

   * Operation is not transactional, and in case of the abort, the result might contain successfully updated records.

   * @param names Distributions to update, you may select `all` to install all compatible distributions
   * @param dir The directory in which distributions reside
   */
  suspend fun update(vararg names: String = ALL_NAMES, dir: String? = null): Result<PythonInstallResponse, ExecError> {
    return executeAndHandleErrors("update", *buildDirOption(dir), *names) { output ->
      Result.success(parsePythonInstallCommandOutput(output))
    }
  }

  private fun buildDirOption(dir: String?): Array<String> = dir?.let { arrayOf("--dir", it) } ?: emptyArray()
}