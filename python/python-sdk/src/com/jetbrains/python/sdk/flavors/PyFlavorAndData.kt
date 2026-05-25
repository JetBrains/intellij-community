package com.jetbrains.python.sdk.flavors

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

data class PyFlavorAndData<D : PyFlavorData, F : PythonSdkFlavor<D>>(val data: D, val flavor: F) {
  val dataClass: Class<D> get() = flavor.flavorDataClass

  @ApiStatus.Internal
  fun sdkSeemsValid(sdk:Sdk, targetConfig: TargetEnvironmentConfiguration?):Boolean = flavor.sdkSeemsValid(sdk, data, targetConfig)
}