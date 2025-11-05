// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.*
import java.util.concurrent.ConcurrentHashMap

internal class PythonPackageManagerServiceImpl(private val serviceScope: CoroutineScope) : PythonPackageManagerService, Disposable {
  private val cache = ConcurrentHashMap<UUID, Deferred<PythonPackageManager>>()

  private val bridgeCache = ConcurrentHashMap<UUID, PythonPackageManagementServiceBridge>()

  /**
   * Requires Sdk to be Python Sdk and have PythonSdkAdditionalData.
   */
  override suspend fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    val cacheKey = (sdk.getOrCreateAdditionalData()).uuid

    return cache.computeIfAbsent(cacheKey) {
      serviceScope.async {
        val createdSdk = PythonPackageManagerProvider.EP_NAME.extensionList.firstNotNullOf { it.createPackageManagerForSdk(project, sdk) }
        Disposer.register(PyPackageCoroutine.getInstance(project), createdSdk)
        PythonRequirementTxtSdkUtils.migrateRequirementsTxtPathFromModuleToSdk(project, sdk)
        createdSdk
      }
    }.await()
  }

  override fun bridgeForSdk(project: Project, sdk: Sdk): PythonPackageManagementServiceBridge {
    val cacheKey = (sdk.sdkAdditionalData as PythonSdkAdditionalData).uuid
    return bridgeCache.computeIfAbsent(cacheKey) {
      val bridge = PythonPackageManagementServiceBridge(project, sdk)
      Disposer.register(this@PythonPackageManagerServiceImpl, bridge)
      bridge
    }
  }

  override fun getServiceScope(): CoroutineScope = serviceScope

  override fun dispose() {
    cache.clear()
  }
}