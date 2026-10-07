// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run.target

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.execution.target.value.constantExplicit
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.community.execService.impl.processLaunchers.getHelpersRootOnEel

/**
 * The request for an SDK without a target on the remote [eel], for example WSL in the eel native mode.
 * Like the requests for SSH and WSL targets, it gives the helpers by their copy on the remote machine.
 * The copy is the one that the eel native mode keeps for all processes, so it is uploaded once.
 */
internal class HelpersAwareEelTargetEnvironmentRequest(private val eel: EelDescriptor) : HelpersAwareTargetEnvironmentRequest {
  /**
   * The request is always local. The platform starts the process on the eel of its working directory.
   * The debugger and the console check for a local request before they open their tunnel to the IDE.
   */
  override val targetEnvironmentRequest: TargetEnvironmentRequest = LocalTargetEnvironmentRequest()

  override fun preparePyCharmHelpers(): PythonHelpersMappings = runBlockingMaybeCancellable {
    val eelApi = eel.toEelApi()
    PythonHelpersMappings(
      getPythonHelpers().map { root -> PathPythonHelpersMapping(root, constantExplicit(getHelpersRootOnEel(eelApi, root).toString())) }
    )
  }
}
