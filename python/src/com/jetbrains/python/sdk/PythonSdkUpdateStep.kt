// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsContexts.NotificationTitle
import com.intellij.python.sdk.backend.PythonEnvironmentContext
import com.intellij.python.sdk.backend.PythonEnvironmentFacts
import com.intellij.python.sdk.backend.PythonEnvironmentKey
import com.intellij.python.sdk.backend.PythonEnvironmentStep
import com.intellij.python.sdk.backend.PythonEnvironments
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdateStep
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyError
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.reloadPackagesBlocking
import com.jetbrains.python.remote.UnsupportedPythonSdkTypeException
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher
import com.jetbrains.python.sdk.targetsFacade.PyTargetsIntrospectionFacade
import com.jetbrains.python.statistics.PythonSDKUpdaterIdsHolder.Companion.REFRESH_SKELETONS_FAILED
import com.jetbrains.python.statistics.PythonSDKUpdaterIdsHolder.Companion.REMOTE_INTERPRETER_SUPPORT_IS_NOT_AVAILABLE
import java.io.IOException

// The steps of an interpreter update that this plugin gives, see PythonInterpreterUpdater. They run in the order of the
// registration in the plugin descriptor.

/** Synchronizes the sources of a target interpreter. It does nothing for a local interpreter. */
internal class PythonSdkSourcesStep : PythonInterpreterUpdateStep {
  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    try {
      coroutineToIndicator { indicator ->
        PyTargetsIntrospectionFacade.create(interpreter.getSdkAPI(), project).synchronizeRemoteSourcesAndSetupMappingsIfNeeded(indicator)
      }
    }
    catch (e: ExecutionException) {
      logger<PythonSdkSourcesStep>().warn("Cannot synchronize the sources of ${interpreter.getSdkAPI().name}", e)
    }
    catch (e: InvalidSdkException) {
      logger<PythonSdkSourcesStep>().warn("Cannot synchronize the sources of ${interpreter.getSdkAPI().name}", e)
    }
    return true
  }
}

/**
 * The background step of an environment update that generates its binary skeletons. A target gets the local copies of
 * its remote roots as its `sys.path`. A failure stays in the facts, and [PythonSdkSkeletonsFailureStep] tells each
 * project about it.
 */
internal class PythonEnvironmentSkeletonsStep : PythonEnvironmentStep {
  override suspend fun update(context: PythonEnvironmentContext, facts: PythonEnvironmentFacts): PythonEnvironmentFacts =
    try {
      val localRoots = coroutineToIndicator { indicator -> generateSkeletons(context.sdk, indicator) }
      if (localRoots != null) facts.copy(sysPath = localRoots) else facts
    }
    catch (e: UnsupportedPythonSdkTypeException) {
      facts.copy(backgroundError = e)
    }
    catch (e: InvalidSdkException) {
      facts.copy(backgroundError = e)
    }
    catch (e: ExecutionException) {
      facts.copy(backgroundError = e)
    }

  /**
   * Generates the skeletons of the environment of [sdk], and returns the local copies of the remote roots of a target,
   * or `null` for a local environment, which reads its `sys.path` with the probe.
   */
  @RequiresBackgroundThread
  @Throws(InvalidSdkException::class, ExecutionException::class)
  private fun generateSkeletons(sdk: Sdk, indicator: ProgressIndicator): List<String>? {
    try {
      sdk.createSkeletonsRootDirectory()
    }
    catch (e: IOException) {
      logger<PythonEnvironmentSkeletonsStep>().warn("Failed to create skeletons directory ${sdk.skeletonsPath}", e)
    }
    // The SDK's own name and path, not its presentation: building one runs the interpreter, which is far too much
    // work for a log line.
    logger<PythonEnvironmentSkeletonsStep>().info("Performing background update of skeletons for SDK ${sdk.name} (${sdk.homePath})")
    indicator.text = PyBundle.message("python.sdk.updating.skeletons")
    // The skeletons belong to the environment, and no project: the daemon restart is up to each project.
    PySkeletonRefresher.refreshSkeletonsOfSdk(null, sdk.skeletonsPath, sdk)
    return if (PythonSdkUtil.isRemote(sdk)) sdk.remoteInterpreterLocalRoots else null
  }
}

/** Tells the user in the project that the skeleton generation of the interpreter failed, see [PythonEnvironmentSkeletonsStep]. */
internal class PythonSdkSkeletonsFailureStep : PythonInterpreterUpdateStep {
  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    val key = PythonEnvironmentKey.of(interpreter.getSdkAPI()) ?: return true
    PythonEnvironments.getInstance().factsIfReady(key)?.backgroundError?.let { error -> notifyOfFailure(project, error, interpreter) }
    return true
  }

  private fun notifyOfFailure(project: Project, exception: Exception, interpreter: PythonInterpreter) {
    val sdk = interpreter.getSdkAPI()
    // The SDK name, which is what the interpreter's presentation would report here anyway, without running it.
    val interpreterName = sdk.name
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      logger<PythonSdkSkeletonsFailureStep>().warn(exception)
      return
    }
    when {
      exception is UnsupportedPythonSdkTypeException -> notifyWarning(
        project,
        PyBundle.message("sdk.gen.failed.notification.title"),
        PyBundle.message("remote.interpreter.support.is.not.available", interpreterName),
        REMOTE_INTERPRETER_SUPPORT_IS_NOT_AVAILABLE,
      )
      exception is InvalidSdkException && PythonSdkUtil.isRemote(sdk) -> PythonSdkType.notifyRemoteSdkSkeletonsFail(exception) {
        if (!(sdk is Disposable && Disposer.isDisposed(sdk))) {
          PythonInterpreterUpdater.getInstance(project).request(sdk, PythonInterpreterUpdateRequest(packages = true, background = false))
        }
      }
      else -> {
        // The interpreter does not run: a user can delete or break it at any moment.
        // That is a problem of the environment, not a defect of the code, so it is a warning and a notification.
        logger<PythonSdkSkeletonsFailureStep>().warn("Skeleton generation failed for $interpreterName", exception)
        if (interpreter.detectedEnvironment != null) {
          notifyWarning(
            project,
            PyBundle.message("sdk.gen.failed.skeletons.title", interpreterName),
            PyBundle.message("sdk.gen.failed.interpreter.unavailable"),
            REFRESH_SKELETONS_FAILED,
          )
        }
      }
    }
  }

  private fun notifyWarning(project: Project, title: @NotificationTitle String, content: @NotificationContent String, displayId: String) {
    NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(title, content, NotificationType.WARNING)
      .setDisplayId(displayId)
      .notify(project)
  }

  private companion object {
    // The ID keeps the old name: the notification settings of a user refer to it.
    const val NOTIFICATION_GROUP_ID = "Python SDK Updater"
  }
}

/** Reads the installed packages again when the request asks for it. */
internal class PythonSdkPackagesStep : PythonInterpreterUpdateStep {
  override suspend fun update(
    project: Project,
    interpreter: PythonInterpreter,
    request: PythonInterpreterUpdateRequest,
    failure: (PyError) -> Unit,
  ): Boolean {
    if (request.packages) {
      val manager = PythonPackageManager.forPythonInterpreter(project, interpreter)
      val sdk = interpreter.getSdkAPI()
      logger<PythonSdkPackagesStep>().info("Performing background scan of packages for SDK ${sdk.name} (${sdk.homePath})")
      coroutineToIndicator { indicator ->
        indicator.isIndeterminate = true
        indicator.text = PyBundle.message("python.sdk.scanning.installed.packages")
        indicator.text2 = ""
        manager.reloadPackagesBlocking()
      }
    }
    return true
  }
}
