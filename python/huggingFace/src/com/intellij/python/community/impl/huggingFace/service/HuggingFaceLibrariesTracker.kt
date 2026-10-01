// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.python.pyproject.model.evolution.pythonInterpreters
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCacheFillService
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.packaging.common.PythonPackageManagementListener
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.hasInstalledPackage
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
      override fun packagesChanged(interpreter: PythonInterpreter) {
        coroutineScope.launch(Dispatchers.IO) {
          updateHFLibraryInstallStatus(interpreter)
        }
      }
    })
  }

  private fun detachSdkListener() {
    connection?.disconnect()
    connection = null
  }

  /** Checks [interpreter] if a Python project of this project uses it. Waits for the first snapshot. */
  private suspend fun updateHFLibraryInstallStatus(interpreter: PythonInterpreter) {
    if (isAnyHFLibraryInstalled) return  // assuming that if was found once - always relevant

    if (interpreter !in project.pythonInterpreters()) return

    if (isAnyHFLibraryInstalledIn(interpreter)) {
      isAnyHFLibraryInstalled = true
      cacheFillService.triggerCacheFillIfNeeded()
      detachSdkListener()
    }
  }

  private suspend fun isAnyHFLibraryInstalledIn(interpreter: PythonInterpreter): Boolean {
    val packageManager = PythonPackageManager.forPythonInterpreter(project, interpreter)
    return relevantLibraries.any { lib ->
      packageManager.hasInstalledPackage(lib)
    }
  }

  override fun dispose(): Unit = detachSdkListener()
}