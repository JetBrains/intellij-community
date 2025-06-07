// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.management

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.packaging.bridge.PythonPackageManagementServiceBridge
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.getOrCreateAdditionalData
import kotlinx.coroutines.CoroutineScope
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class PythonPackageManagerServiceImpl(private val serviceScope: CoroutineScope) : PythonPackageManagerService, Disposable {
  private val cache = ConcurrentHashMap<UUID, PythonPackageManager>()

  private val bridgeCache = ConcurrentHashMap<UUID, PythonPackageManagementServiceBridge>()

  /**
   * Requires Sdk to be Python Sdk and have PythonSdkAdditionalData.
   */
  override fun forSdk(project: Project, sdk: Sdk): PythonPackageManager {
    val cacheKey = (sdk.getOrCreateAdditionalData()).uuid

    return cache.computeIfAbsent(cacheKey) {
      PythonPackageManagerProvider.EP_NAME.extensionList
        .firstNotNullOf { it.createPackageManagerForSdk(project, sdk) }
    }
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