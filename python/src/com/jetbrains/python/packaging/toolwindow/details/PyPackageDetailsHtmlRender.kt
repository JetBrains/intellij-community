// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.asBinToExec
import com.intellij.python.community.execService.python.PyHelper
import com.intellij.python.community.execService.python.StdInProvider
import com.intellij.python.community.execService.python.executeHelper
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.sdk.asLocalHelperPython
import com.jetbrains.python.sdk.findLocalHelperPython
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The RST helper, see `PythonHelper.REST_RUNNER`. */
private val REST_RUNNER: PyHelper = PyHelper("rest_runners/rst2smth.py")

internal class PyPackageDetailsHtmlRender(val project: Project, val currentInterpreter: PythonInterpreter?) {
  suspend fun getHtml(packageDetails: PythonPackageDetails): String {
    return runCatching {
      with(packageDetails) {
        when {
          !description.isNullOrEmpty() -> convertToHTML(descriptionContentType, description!!)
          !summary.isNullOrEmpty() -> wrapHtml(summary!!)
          else -> NO_DESCRIPTION
        }
      }
    }.getOrElse {
      thisLogger().info(it)
      message("conda.packaging.error.rendering.description")
    }
  }

  private suspend fun convertToHTML(contentType: String?, description: String): String {
    return withContext(Dispatchers.IO) {
      when (contentType?.split(';')?.firstOrNull()?.trim()) {
        "text/markdown" -> markdownToHtml(description)
        "text/x-rst", "" -> rstToHtml(description, currentInterpreter!!)
        else -> description
      }
    }
  }

  /**
   * Renders [text] with the RST helper. The helper runs on the interpreter of the tool window when that is a local
   * CPython, else on the local helper Python, see [findLocalHelperPython]. The helper dependencies are for Python 3 only.
   */
  private suspend fun rstToHtml(text: String, interpreter: PythonInterpreter): String {
    val python = interpreter.asLocalHelperPython() ?: findLocalHelperPython(project, null)
    if (python == null || python.languageLevel.isPython2)
      return wrapHtml("<p>${message("python.toolwindow.packages.documentation.local.interpreter")}</p>")

    val result = withBackgroundProgress(project, message("python.toolwindow.packages.converting.description.progress"), cancellable = true) {
      ExecService().executeHelper(python = python.binary.asBinToExec(),
                                  helper = REST_RUNNER,
                                  helperArgs = Args("rst2html_no_code"),
                                  stdInProvider = StdInProvider(text.toByteArray()))
    }
    return when (result) {
      is Result.Success -> result.result
      is Result.Failure -> {
        thisLogger().info("Cannot render the RST description: ${result.error.message}")
        wrapHtml("<p>${message("python.toolwindow.packages.rst.parsing.failed")}</p>")
      }
    }
  }

  private fun markdownToHtml(text: String): String {
    val mdHtml = this::class.java.getResource("/packaging/md.template.html")?.readText() ?: error("Cannot get md template")
    val quotedText = text.replace("`", "\\`")

    val prepared = mdHtml.replace("{MD_TEXT}", "\n" + quotedText)
    return prepared
  }

  private fun wrapHtml(html: String): String = "<html><head></head><body><p>$html</p></body></html>"

  private val NO_DESCRIPTION: String
    get() = message("python.toolwindow.packages.no.description.placeholder")

}