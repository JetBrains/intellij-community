// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.errorProcessing.PyResult
import org.jetbrains.annotations.ApiStatus

/**
 * Used on directory opening with an attempt to configure suitable Python interpreter
 * (mentioned below as sdk configurator).
 *
 * Used with an attempt to suggest suitable Python interpreter
 * or try setup and register it in case of headless mode if no interpreter is specified.
 */
@ApiStatus.Internal
interface PyProjectSdkConfigurationExtension {

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName.create<PyProjectSdkConfigurationExtension>("Pythonid.projectSdkConfigurationExtension")

    @JvmStatic
    @RequiresBackgroundThread
    fun findForModule(module: Module): Pair<@IntentionName String, PyProjectSdkConfigurationExtension>? = runBlockingMaybeCancellable {
      EP_NAME.extensionsIfPointIsRegistered.firstNotNullOfOrNull { ext -> ext.getIntention(module)?.let { Pair(it, ext) } }
    }
  }

  /**
   * An implementation is responsible for interpreter setup and registration in IDE.
   * In case of failures `null` should be returned, the implementation is responsible for errors displaying.
   *
   * Rule of thumb is to explicitly ask a user if sdk creation is desired and allowed.
   */
  suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk?>

  /**
   * An implementation is responsible for interpreter setup and registration in IDE.
   * In case of failures `null` should be returned, the implementation is responsible for errors displaying.
   *
   * You're free here to create sdk immediately, without any user permission since quick fix is explicitly clicked.
   */
  suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk?>

  /**
   * Called by sdk configurator and interpreter inspection
   * to determine if an extension could configure or suggest an interpreter for the passed [module].
   *
   * First applicable extension is processed, others are ignored.
   * If there is no applicable extension, configurator and inspection guess a suitable interpreter.
   *
   * Could be called from AWT hence should be as fast as possible.
   *
   * If returned value is `null`, then the extension can't be used to configure an interpreter (not applicable).
   * Otherwise returned string is used as a quick fix name.
   *
   * Example: `Create a virtual environment using requirements.txt`.
   */
  @IntentionName
  suspend fun getIntention(module: Module): String?

  /**
   * If headless supported implementation is responsible for interpreter setup and registration
   * for [createAndAddSdkForConfigurator] method in IDE without an additional user input.
   */
  fun supportsHeadlessModel(): Boolean = false
}
