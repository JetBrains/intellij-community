package com.intellij.python.sdk.ui.evolution.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.PythonSdkUtil
import javax.swing.Icon


data class EvoModuleSdk(val module: Module, val evoSdk: EvoSdk?) {
  fun getIcon(): Icon = evoSdk?.icon ?: AllIcons.General.BalloonWarning

  //
  //fun getState(): EvoSdkState = when {
  //  evoSdk == null -> EvoSdkState.UNDEFINED
  //  !evoSdk.sdkSeemsValid || PythonSdkType.hasInvalidRemoteCredentials(evoSdk) -> EvoSdkState.INVALID
  //  PythonSdkType.isIncompleteRemote(evoSdk) -> EvoSdkState.INCOMPLETE
  //  !LanguageLevel.SUPPORTED_LEVELS.contains(PySdkUtil.getLanguageLevelForSdk(evoSdk)) -> EvoSdkState.UNSUPPORTED
  //  else -> EvoSdkState.OK
  //}

  companion object {
    fun findForModule(module: Module): Pair<Sdk?, EvoModuleSdk> {
      val pySdk = PythonSdkUtil.findPythonSdk(module) ?: return null to EvoModuleSdk(module, null)
      val evoSdk = EVO_SDK_PROVIDERS.firstNotNullOf { it.parsePySdk(module, pySdk) }
      return pySdk to EvoModuleSdk(module, evoSdk)
    }
  }
}