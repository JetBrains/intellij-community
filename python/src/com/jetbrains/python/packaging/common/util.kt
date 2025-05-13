// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsContexts
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.PyPackagesNotificationPanel
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

@ApiStatus.Experimental
interface PythonPackageManagementListener {
  fun packagesChanged(sdk: Sdk)
}

internal class PythonRankingAwarePackageNameComparator : Comparator<String> {
  val ranking = service<PyPIPackageRanking>().packageRank
  override fun compare(name1: String, name2: String): Int {
    val rank1 = ranking[name1.lowercase()]
    val rank2 = ranking[name2.lowercase()]
    return when {
      rank1 != null && rank2 == null -> -1
      rank1 == null && rank2 != null -> 1
      rank1 != null && rank2 != null && rank1 != rank2 -> rank2 - rank1
      else -> String.CASE_INSENSITIVE_ORDER.compare(name1, name2)
    }
  }
}

internal suspend fun <T> runPackagingOperationOrShowErrorDialog(
  sdk: Sdk,
  @NlsContexts.DialogTitle title: String,
  packageName: String? = null,
  operation: suspend (() -> Result<T>),
): Result<T> {
  try {
    val result = withContext(Dispatchers.IO) {
      operation.invoke()
    }
    result.exceptionOrNull()?.let { throw it }
    return result
  }
  catch (ex: PyExecutionException) {
    val description = PyPackageManagementService.toErrorDescription(listOf(ex), sdk, packageName)
    if (!PythonPackageManagementServiceBridge.runningUnderOldUI) {
      // todo[akniazev] this check is used for legacy package management only, remove when it's not needed anymore
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          if (packageName != null) {
            PyPackagesUsageCollector.failInstallSingleEvent.log()
            PyPackagesNotificationPanel.showPackageInstallationError(title, description!!)
          }
          else {
            PackagesNotificationPanel.showError(title, description!!)
          }
        }
      }
    }
    return Result.failure(ex)
  }
  catch (ex: CancellationException) {
    //ignore without logging
    return Result.failure(ex)
  }
  catch (ex: Throwable) {
    logger<PythonPackageManager>().error("Exception during $title", ex)
    return Result.failure(ex)
  }
}