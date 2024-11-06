// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCacheFillService
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.packaging.PyPackageInstallUtils
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.sdk.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFaceLibrariesTracker(
  private val project: Project,
  private val coroutineScope: CoroutineScope
) : Disposable {
  @Volatile private var isAnyHFLibraryInstalled: Boolean = false
  private var connection: MessageBusConnection? = project.messageBus.connect(this)
  private val cacheFillService: HuggingFaceCacheFillService = project.getService(HuggingFaceCacheFillService::class.java)

  private val relevantLibraries = setOf(
    "diffusers", "transformers", "allennlp", "spacy",
    "asteroid", "flair", "keras", "sentence-transformers",
    "stable-baselines3", "adapters", "huggingface_hub"
  )

  init {
    setupSdkListener()
  }

  fun isAnyHFLibraryInstalled(): Boolean = isAnyHFLibraryInstalled

  private fun setupSdkListener() {
    connection?.subscribe(PythonPackageManager.PACKAGE_MANAGEMENT_TOPIC, object : PythonPackageManagementListener {
      override fun packagesChanged(sdk: Sdk) {
        val projectSdk = getProjectPythonSdk()

        if (sdk == projectSdk) {
          coroutineScope.launch(Dispatchers.IO) {
            updateHFLibraryInstallStatus()
          }
        }
      }
    })
  }

  private fun detachSdkListener() {
    connection?.disconnect()
    connection = null
  }

  private fun getProjectPythonSdk(): Sdk? = PythonSdkUtil.findPythonSdk(project.modules.firstOrNull())

  private fun updateHFLibraryInstallStatus() {
    if (isAnyHFLibraryInstalled) return  // assuming that if was found once - always relevant

    val sdk = getProjectPythonSdk() ?: return

    if (isAnyHFLibraryInstalledInSdk(sdk)) {
      isAnyHFLibraryInstalled = true
      cacheFillService.triggerCacheFillIfNeeded()
      detachSdkListener()
    }
  }

  private fun isAnyHFLibraryInstalledInSdk(sdk: Sdk): Boolean = relevantLibraries.any { lib ->
    PyPackageInstallUtils.getPackageVersion(project, sdk, lib) != null
  }

  override fun dispose() = detachSdkListener()
}