// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.details

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.target.TargetProgressIndicator
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.helpersLocator.PythonHelpersLocator
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.packaging.common.PythonPackageDetails
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.run.applyHelperPackageToPythonPath
import com.jetbrains.python.run.buildTargetedCommandLine
import com.jetbrains.python.run.prepareHelperScriptExecution
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.sdkFlavor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PyPackageDetailsHtmlRender(val project: Project, val currentSdk: Sdk?) {
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
        "text/x-rst", "" -> rstToHtml(description, currentSdk!!)
        else -> description
      }
    }
  }

  private suspend fun rstToHtml(text: String, sdk: Sdk): String {
    val localSdk = PythonSdkType.findLocalCPythonForSdk(sdk)
    if (localSdk == null)
      return wrapHtml("<p>${message("python.toolwindow.packages.documentation.local.interpreter")}</p>")

    val helpersAwareTargetRequest = PythonInterpreterTargetEnvironmentFactory.findPythonTargetInterpreter(localSdk, project)
    val targetEnvironmentRequest = helpersAwareTargetRequest.targetEnvironmentRequest
    val pythonExecution = prepareHelperScriptExecution(PythonHelper.REST_RUNNER, helpersAwareTargetRequest)

    // todo[akniazev]: this workaround should can be removed when PY-57134 is fixed
    val helperLocation = if (localSdk.sdkFlavor.getLanguageLevel(localSdk).isPython2) "py2only" else "py3only"
    val path = PythonHelpersLocator.findPathStringInHelpers(helperLocation)
    pythonExecution.applyHelperPackageToPythonPath(listOf(path), helpersAwareTargetRequest)

    pythonExecution.addParameter("rst2html_no_code")
    val targetProgressIndicator = TargetProgressIndicator.EMPTY
    val targetEnvironment = targetEnvironmentRequest.prepareEnvironment(targetProgressIndicator)

    targetEnvironment.uploadVolumes.entries.forEach { (_, value) ->
      value.upload(".", targetProgressIndicator)
    }

    val targetedCommandLine = pythonExecution.buildTargetedCommandLine(targetEnvironment, localSdk, emptyList())

    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val process = targetEnvironment.createProcess(targetedCommandLine, indicator)

    val commandLine = targetedCommandLine.collectCommandsSynchronously()
    val commandLineString = commandLine.joinToString(" ")

    val handler = CapturingProcessHandler(process, targetedCommandLine.charset, commandLineString)

    val output = withBackgroundProgress(project, message("python.toolwindow.packages.converting.description.progress"), cancellable = true) {
      reportRawProgress {
        val processInput = handler.processInput
        processInput.use {
          processInput.write(text.toByteArray())
        }
        handler.runProcess(10 * 60 * 1000)
      }
    }

    return when {
      output.checkSuccess(thisLogger()) -> output.stdout
      else -> wrapHtml("<p>${message("python.toolwindow.packages.rst.parsing.failed")}</p>")
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