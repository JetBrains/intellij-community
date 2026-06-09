// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.util.ui.launchOnShow
import com.jetbrains.python.sdk.isSdkConfigurationInProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.function.Consumer
import javax.swing.JComponent

/**
 * Observes [Project.isSdkConfigurationInProgress] while [uiComponent] is showing and reflects the
 * lock state in the UI.
 *
 * The collection is bound to the component's visibility via [launchOnShow], so it is restarted on
 * re-show and cancelled on hide. Callbacks are invoked on the EDT under [Dispatchers.EDT] (which
 * permits RW-lock access, unlike the pure-UI dispatcher [launchOnShow] runs the block on), so they
 * may touch the platform model (e.g. reload the SDK list):
 *  - [onInProgressChanged] fires for every state value (including the initial one), so the caller
 *    can keep the controls' enabled state in sync idempotently;
 *  - [onReleased] fires only on a real locked -> unlocked transition, so the caller can refresh
 *    controls that may have gone stale while the background SDK configuration was running.
 */
internal fun observeSdkConfigurationInProgress(
  project: Project,
  uiComponent: JComponent,
  onInProgressChanged: Consumer<Boolean>,
  onReleased: Runnable,
) {
  uiComponent.launchOnShow("PyActiveSdkConfigurable.observeSdkConfigurationInProgress") {
    var wasInProgress = false
    project.isSdkConfigurationInProgress.collect { inProgress ->
      withContext(Dispatchers.EDT) {
        onInProgressChanged.accept(inProgress)
        if (wasInProgress && !inProgress) {
          onReleased.run()
        }
      }
      wasInProgress = inProgress
    }
  }
}
