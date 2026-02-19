package com.intellij.python.sdk.ui.evolution.tool.hatch.sdk

//import com.intellij.python.hatch.HatchConfiguration
//import com.intellij.python.hatch.getHatchService
//import com.intellij.python.hatch.icons.PythonHatchIcons
//import com.jetbrains.python.hatch.sdk.HatchSdkAdditionalData
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdk
import com.intellij.python.sdk.ui.evolution.sdk.EvoSdkProvider
import com.intellij.python.sdk.ui.icons.PythonSdkUIIcons
import com.jetbrains.python.PythonBinary
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object HatchEvoSdkManager  {
  fun buildEvoSdk(pythonBinaryPath: PythonBinary?, name: String): EvoSdk {
    return EvoSdk(
      icon = PythonSdkUIIcons.Logo,
      name = name,
      pythonBinaryPath = pythonBinaryPath,
    )
  }
}

internal object HatchEvoSdkProvider : EvoSdkProvider {
  override fun parsePySdk(module: Module, sdk: Sdk): EvoSdk? {
    //val data = sdk.sdkAdditionalData as? HatchSdkAdditionalData ?: return null
    //
    //val pythonBinaryPath = sdk.homePath?.let { Path.of(it) } ?: return null
    //val evoSdk = HatchEvoSdkManager.buildEvoSdk(pythonBinaryPath, data.hatchEnvironmentName ?: "default")
    //
    //return evoSdk
    return null
  }
}