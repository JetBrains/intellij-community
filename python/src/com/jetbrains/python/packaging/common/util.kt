// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.PyPackagesNotificationPanel
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import com.jetbrains.python.statistics.PyPackagesUsageCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class PackageManagerHolder : Disposable {
  private val cache = ConcurrentHashMap<UUID, PythonPackageManager>()

  private val bridgeCache = ConcurrentHashMap<UUID, PythonPackageManagementServiceBridge>()

  /**
   * Requires Sdk to be Python Sdk and have PythonSdkAdditionalData.
   */
  fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    val cacheKey = (sdk.getOrCreateAdditionalData()).uuid

    return cache.computeIfAbsent(cacheKey) {
     PythonPackageManagerProvider.EP_NAME.extensionList
        .firstNotNullOf { it.createPackageManagerForSdk(project, sdk) }
    }
  }

  fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    val cacheKey = (sdk.sdkAdditionalData as PythonSdkAdditionalData).uuid
    return bridgeCache.computeIfAbsent(cacheKey) {
      val bridge = PythonPackageManagementServiceBridge(project, sdk)
      Disposer.register(this@PackageManagerHolder, bridge)
      bridge
    }
  }

  override fun dispose() {
    cache.clear()
  }
}

@ApiStatus.Experimental
interface PythonPackageManagementListener {
  fun packagesChanged(sdk: Sdk)
}

class PythonRankingAwarePackageNameComparator : Comparator<String> {
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


suspend fun <T> runPackagingOperationOrShowErrorDialog(
  sdk: Sdk,
  @NlsContexts.DialogTitle title: String,
  packageName: String? = null,
  operation: suspend (() -> Result<T>),
): Result<T> {
  try {
    return operation.invoke()
  }
  catch (ex: PyExecutionException) {
    val description = PyPackageManagementService.toErrorDescription(listOf(ex), sdk, packageName)
    if (!PythonPackageManagementServiceBridge.runningUnderOldUI) {
      // todo[akniazev] this check is used for legacy package management only, remove when it's not needed anymore
      withContext(Dispatchers.Main) {
        if (packageName != null) {
          PyPackagesUsageCollector.failInstallSingleEvent.log()
          PyPackagesNotificationPanel.showPackageInstallationError(title, description!!)
        }
        else {
          PackagesNotificationPanel.showError(title, description!!)
        }
      }
    }
    return Result.failure(ex)
  }
  catch (ex: Throwable) {
    return Result.failure(ex)
  }
}