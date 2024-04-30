// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.io.await
import com.jetbrains.python.newProject.FrameworkInstaller.Companion.installFrameworksInBackground
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * `suspend`-friendly frontend for [PythonProjectGenerator]
 * Installs frameworks in the background, shows errors as messages.
 * Use [installFrameworksInBackground]
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class FrameworkInstaller private constructor(private val projectScope: CoroutineScope) {
  companion object {
    private val log = Logger.getInstance(FrameworkInstaller::class.java)

    /**
     * Install python [frameworks] (as in `requirements.txt`) on [sdk] as background a task.
     * `null` in [sdk] will lead to an error displayed in UI (for ar backward compatibility).
     *
     * This function is a wrapper for [PythonProjectGenerator.installFrameworkInBackground]
     */
    fun installFrameworksInBackground(project: Project, sdk: Sdk?, vararg frameworks: @NonNls String) {
      val taskName = "Python framework installer for ${frameworks.joinToString(",")}"
      log.info("Started $taskName")
      // Do not wait for a modal dialog to close
      val context = Dispatchers.EDT + ModalityState.current().asContextElement() + CoroutineName(taskName)
      project.service<FrameworkInstaller>().projectScope.launch(context) {
        for (framework in frameworks) {
          log.info("installing $framework")
          PythonProjectGenerator.installFrameworkInBackground(project, framework, framework, sdk, true, null).await()
          log.info("$framework installed")
        }
        log.info("Completed $taskName")
      }
    }
  }
}
