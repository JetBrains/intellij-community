// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.common

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.NlsContexts
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPIPackageRanking
import com.jetbrains.python.packaging.PyPackagesNotificationPanel
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

@Service
class PackageManagerHolder {
  private val cache = mutableMapOf<UUID, PythonPackageManager>()

  /**
   * Requires Sdk to be Python Sdk and have PythonSdkAdditionalData.
   */
  fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    val cacheKey = (sdk.sdkAdditionalData as PythonSdkAdditionalData).uuid
    if (cacheKey in cache) {
      val manager = cache[cacheKey]!!
      if (manager.project == project) return manager
    }

    val manager = PythonPackageManagerProvider.EP_NAME.extensionList
      .firstNotNullOf { it.createPackageManagerForSdk(project, sdk) }

    cache[cacheKey] = manager
    return manager
  }
}

@ApiStatus.Experimental
interface PythonPackageManagementListener {
  fun packagesChanged(sdk: Sdk)
}

internal val RANKING_AWARE_PACKAGE_NAME_COMPARATOR: java.util.Comparator<String> = Comparator { name1, name2 ->
  val ranking = service<PyPIPackageRanking>().packageRank
  val rank1 = ranking[name1.lowercase()]
  val rank2 = ranking[name2.lowercase()]
  return@Comparator when {
    rank1 != null && rank2 == null -> -1
    rank1 == null && rank2 != null -> 1
    rank1 != null && rank2 != null -> rank2 - rank1
    else -> String.CASE_INSENSITIVE_ORDER.compare(name1, name2)
  }
}


suspend fun <T> runPackagingOperationOrShowErrorDialog(sdk: Sdk,
                                                       @NlsContexts.DialogTitle title: String,
                                                       packageName: String? = null,
                                                       operation: suspend (() -> Result<T>)): Result<T> {
  try {
    return operation.invoke()
  }
  catch (ex: PyExecutionException) {
    val description = PyPackageManagementService.toErrorDescription(listOf(ex), sdk, packageName)
    withContext(Dispatchers.Main) {
      if (packageName != null) PyPackagesNotificationPanel.showPackageInstallationError(title, description!!)
      else PackagesNotificationPanel.showError(title, description!!)
    }
    return Result.failure(ex)
  }
}