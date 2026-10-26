package com.jetbrains.python.sdk.flavors

import com.intellij.python.sdk.common.PyEnvRef
import com.jetbrains.python.sdk.add.v2.PathHolder
import org.jetbrains.annotations.ApiStatus

data class PyFlavorAndData<D : PyFlavorData, F : PythonSdkFlavor<D>>(val data: D, val flavor: F) {
  val dataClass: Class<D> get() = flavor.flavorDataClass

  /** The env ref the tool of this flavor gives the environment at [pythonBinary], see [PythonSdkFlavor.toolEnvRefOf]. */
  @ApiStatus.Internal
  fun toolEnvRefOf(pythonBinary: PathHolder): PyEnvRef? = flavor.toolEnvRefOf(pythonBinary, data)?.let(::PyEnvRef)
}