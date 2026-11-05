// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.bridge

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Keeps one [PythonPackageManagementServiceBridge] for each Python SDK of the project. */
@Service(Service.Level.PROJECT)
internal class PythonPackageManagementServiceBridges(private val project: Project) : Disposable {
  private val cache = ConcurrentHashMap<UUID, PythonPackageManagementServiceBridge>()

  /** The bridge of [sdk]. The SDK must be a Python SDK with [PythonSdkAdditionalData]. */
  fun bridgeForSdk(sdk: Sdk): PythonPackageManagementServiceBridge {
    val cacheKey = (sdk.sdkAdditionalData as PythonSdkAdditionalData).uuid
    return cache.computeIfAbsent(cacheKey) {
      val bridge = PythonPackageManagementServiceBridge(project, sdk)
      Disposer.register(this, bridge)
      bridge
    }
  }

  override fun dispose(): Unit = Unit
}