// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.processTools.mapFlat
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isNotificationSilentMode
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.community.impl.installer.CondaInstallManager.installLatest
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.Product
import com.jetbrains.python.sdk.add.v1.loadLocalPythonCondaPath
import com.jetbrains.python.sdk.add.v1.saveLocalPythonCondaPath
import com.jetbrains.python.sdk.conda.condaSupportedLanguages
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.pythonSdk
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.Nls
import java.nio.file.Path

private val logger = fileLogger()

internal class PySetUpCondaFix : LocalQuickFix {
  override fun getFamilyName(): @IntentionFamilyName String {
    return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter")
  }

  override fun getName(): @IntentionName String {
    return PyBundle.message("sdk.create.custom.venv.install.fix.title", Product.Miniconda.title, "")
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    var condaPath = getCondaPath(project)
    if (condaPath == null) {
      condaPath = installConda(project)
    }
    if (condaPath != null) {
      setUpCondaInterpreter(project, condaPath)
    }
    else {
      notifyAboutError(project, PyBundle.message("python.sdk.conda.no.exec"), "no.conda.executable")
    }
  }

  private fun setUpCondaInterpreter(project: Project, condaPath: FullPathOnTarget) {
    runWithModalProgressBlocking(project, PyBundle.message("python.sdk.creating.conda.environment.sentence")) {
      reportRawProgress { reporter ->
        createCondaSdk(project, condaPath, reporter)?.let { sdk ->
          SdkConfigurationUtil.addSdk(sdk)
          project.pythonSdk = sdk
          for (module in ModuleManager.getInstance(project).modules) {
            setReadyToUseSdk(project, module, sdk)
          }
        }
      }
    }
  }

  private suspend fun createCondaSdk(
    project: Project,
    condaPath: FullPathOnTarget,
    reporter: RawProgressReporter,
  ): Sdk? {
    val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
    val maxCondaSupportedLanguage = condaSupportedLanguages.maxWith(LanguageLevel.VERSION_COMPARATOR)
    val envRequest = NewCondaEnvRequest.EmptyNamedEnv(maxCondaSupportedLanguage, project.name)
    return runCatching {
      PyCondaCommand(condaPath, null, project)
        .createCondaSdkAlongWithNewEnv(envRequest, Dispatchers.EDT, existingSdks.toList(), project, reporter)
    }.mapFlat { it }.reportError(project, "Failed to set up conda sdk", "conda.sdk.setup.failed")
  }

  private fun installConda(project: Project): FullPathOnTarget? {
    runCatching { installLatest(project, Product.Miniconda) }
      .reportError(project, "Failed to install conda", "conda.failed.to.install")
    return getCondaPath(project)
  }

  private fun <T> Result<T>.reportError(project: Project, message: String, displayId: String): T? {
    exceptionOrNull()?.takeIf { it !is CancellationException }?.let { th ->
      logger.warn(message, th)
      notifyAboutError(project, th.message ?: message, displayId)
    }
    return getOrNull()
  }

  private fun getCondaPath(project: Project): FullPathOnTarget? {
    loadLocalPythonCondaPath()?.let {
      return FullPathOnTarget(it.toString())
    }
    val path = runWithModalProgressBlocking(project, PyBundle.message("python.add.sdk.conda.detecting")) {
      suggestCondaPath()
    }
    path?.let {
      saveLocalPythonCondaPath(Path.of(path))
    }
    return path
  }

  private fun notifyAboutError(project: Project, text: @Nls String, displayId: String) {
    if (isNotificationSilentMode(project)) return
    NotificationGroupManager.getInstance().getNotificationGroup("ConfiguredPythonInterpreter").createNotification(
      text,
      NotificationType.ERROR
    )
      .setDisplayId(displayId)
      .notify(project)
  }

}
