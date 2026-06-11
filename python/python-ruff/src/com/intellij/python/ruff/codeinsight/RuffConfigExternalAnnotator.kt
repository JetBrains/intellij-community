// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffBundle
import com.jetbrains.python.sdk.getExecutablePath
import com.jetbrains.python.sdk.pythonSdk
import org.toml.lang.psi.TomlFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.writeText

/**
 * External annotator for Ruff config files (.ruff.toml, ruff.toml, and pyproject.toml).
 */
class RuffConfigExternalAnnotator : ExternalAnnotator<RuffConfigExternalAnnotator.State, RuffConfigExternalAnnotator.Result>() {

  companion object {
    private val LOG = logger<RuffConfigExternalAnnotator>()

  }

  data class State(
    val project: Project,
    val file: PsiFile,
    val virtualFile: VirtualFile,
    val tempFile: Path,
  )

  class Result(
    val errorLine: Int = -1,
    val errorColumn: Int = -1,
    val errorWidth: Int = -1,
    val errorMessage: String = "",
   )

  /**
   * Collects information from the file to be used for annotation.
   */
  override fun collectInformation(file: PsiFile): State? {
    // Only process TOML files
    if (file !is TomlFile) return null

    if (!file.isRuffConfigFile) return null

    val virtualFile = file.virtualFile ?: return null

    val tempDir = Files.createTempDirectory(null)
    val tempFile = tempDir / file.name
    tempFile.writeText(file.text)
    return State(file.project, file, virtualFile, tempFile)
  }

  /**
   * Executes the external tool and processes its output.
   */
  override fun doAnnotate(state: State): Result? {
    val module = ModuleUtilCore.findModuleForFile(state.virtualFile, state.project)
    val sdk = module?.pythonSdk ?: state.project.pythonSdk ?: return null

    val ruffExecutable = sdk.getExecutablePath("ruff") ?: return null.also {
      LOG.info("Could not find ruff executable in SDK: ${sdk.name}")
    }

    val workingDir = state.virtualFile.parent.path

    val processBuilder = ProcessBuilder(ruffExecutable.absolutePathString(), "check", "?", "--config", state.tempFile.absolutePathString())
      .directory(File(workingDir))
      .redirectErrorStream(true)

    try {
      val process = processBuilder.start()
      val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }

      return parseOutput(output)
    }
    catch (e: Exception) {
      LOG.warn("Error executing ruff check ? command", e)
      return null
    }
  }

  private fun parseOutput(output: String): Result? {
    if (SUCCESS_PATTERN matches output) {
      return null
    }

    val match = ERROR_PATTERN.find(output) ?: return null.also {
      LOG.info("Could not parse `ruff check ?` output: $output")
    }

    return Result(
      errorLine = match.groups[1]!!.value.toInt(),
      errorColumn = match.groups[2]!!.value.toInt(),
      errorWidth = match.groups[3]!!.value.length,
      errorMessage = match.groups[4]!!.value
    )
  }

  override fun apply(file: PsiFile, result: Result?, holder: AnnotationHolder) {
    if (result == null) return

    val document = file.viewProvider.document ?: return

    val lineStartOffset = document.getLineStartOffset(result.errorLine - 1)

    val startOffset = lineStartOffset + result.errorColumn - 1
    val endOffset = startOffset + result.errorWidth

    val message = RuffBundle.message("inspection.message.ruff.config.error", result.errorMessage)
    holder.newAnnotation(HighlightSeverity.ERROR, message)
      .range(TextRange(startOffset, endOffset))
      .tooltip(buildTooltip(result))
      .create()

  }

  @NlsSafe
  private fun buildTooltip(result: Result): String {
    return """
            <html>
            <body>
            <p><b>Ruff config error:</b> ${result.errorMessage}</p>
            </body>
            </html>
        """.trimIndent()
  }
}

private val ERROR_PATTERN = Regex(
  """
  ruff failed
    (?:Cause: Failed to load configuration `.+`
    )?Cause: Failed to parse .+
    Cause: TOML parse error at line (\d+), column (\d+)
   \s*\|
  \d+ \| .*
   \s*\|\s+(\^+)
  (.+)
  """.trimIndent()
)

// Pattern to match the success message from ruff check ? output
private val SUCCESS_PATTERN = Regex("\\?:1:1: E902 No such file or directory \\(os error 2\\)\\s+Found 1 error\\.")