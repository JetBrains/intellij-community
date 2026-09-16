// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ruff.codeinsight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.PsiFile
import com.intellij.python.ruff.RuffBundle
import com.jetbrains.python.Result
import com.jetbrains.python.sdk.ModuleOrProject
import org.toml.lang.psi.TomlFile

/**
 * External annotator for Ruff config files (.ruff.toml, ruff.toml, and pyproject.toml).
 */
internal class RuffConfigExternalAnnotator : ExternalAnnotator<RuffConfigExternalAnnotator.State, RuffConfigError>() {

  companion object {
    private val LOG = logger<RuffConfigExternalAnnotator>()
  }

  /**
   * The config file as [collectInformation] read it.
   *
   * It holds the text and not the [PsiFile], because [doAnnotate] runs later and outside a read
   * action.
   */
  data class State(
    val project: Project,
    val virtualFile: VirtualFile,
    val fileName: String,
    val text: String,
  )

  /**
   * Collects information from the file to be used for annotation.
   */
  override fun collectInformation(file: PsiFile): State? {
    // Only process TOML files
    if (file !is TomlFile) return null

    if (!file.isRuffConfigFile) return null

    val virtualFile = file.virtualFile ?: return null

    return State(file.project, virtualFile, file.name, file.text)
  }

  /**
   * Executes the external tool and processes its output.
   */
  override fun doAnnotate(state: State): RuffConfigError? {
    // A config file can live outside any nio filesystem, for example inside an archive.
    val workingDir = state.virtualFile.parent?.toNioPathOrNull() ?: return null
    val module = ModuleUtilCore.findModuleForFile(state.virtualFile, state.project)
    val moduleOrProject = module?.let { ModuleOrProject.ModuleAndProject(it) } ?: ModuleOrProject.ProjectOnly(state.project)

    val result = runBlockingMaybeCancellable {
      checkRuffConfig(moduleOrProject, state.fileName, state.text, workingDir)
    }
    return when (result) {
      is Result.Failure -> {
        // TODO: Come with a solution to report background errors to user
        LOG.warn("Cannot check the Ruff config: ${result.error.message}")
        null
      }
      is Result.Success -> result.result
    }
  }

  override fun apply(file: PsiFile, result: RuffConfigError?, holder: AnnotationHolder) {
    if (result == null) return

    val document = file.viewProvider.document ?: return

    val lineStartOffset = document.getLineStartOffset(result.line - 1)

    val startOffset = lineStartOffset + result.column - 1
    val endOffset = startOffset + result.width

    val message = RuffBundle.message("inspection.message.ruff.config.error", result.message)
    holder.newAnnotation(HighlightSeverity.ERROR, message)
      .range(TextRange(startOffset, endOffset))
      .tooltip(buildTooltip(result))
      .create()
  }

  @NlsSafe
  private fun buildTooltip(result: RuffConfigError): String {
    return """
            <html>
            <body>
            <p><b>Ruff config error:</b> ${result.message}</p>
            </body>
            </html>
        """.trimIndent()
  }
}
