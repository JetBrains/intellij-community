package com.jetbrains.python.sdk.flavors

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor.UnknownFlavor

typealias UnknownFlavorAndData = PyFlavorAndData<PyFlavorData.Empty, UnknownFlavor>

data class PyFlavorAndData<D : PyFlavorData, F : PythonSdkFlavor<D>>(val data: D, val flavor: F) {
  val dataClass: Class<D> get() = flavor.flavorDataClass

  fun sdkSeemsValid(sdk:Sdk, targetConfig: TargetEnvironmentConfiguration?):Boolean = flavor.sdkSeemsValid(sdk, data, targetConfig)

  companion object {
    @JvmStatic
    val UNKNOWN_FLAVOR_DATA: UnknownFlavorAndData = PyFlavorAndData(PyFlavorData.Empty, UnknownFlavor.INSTANCE)
  }
}