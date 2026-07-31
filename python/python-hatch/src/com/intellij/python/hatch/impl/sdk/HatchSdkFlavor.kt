package com.intellij.python.hatch.impl.sdk

import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.hatch.icons.PythonHatchIcons
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorData
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon

@ApiStatus.Internal
@PyInternalExecApi
data class HatchSdkFlavorData(val hatchEnvironmentName: String?) : PyFlavorData {
  override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
    PyFlavorData.Empty.prepareTargetCommandLine(sdk, targetCommandLineBuilder)
  }
}

@ApiStatus.Internal
@PyInternalExecApi
object HatchSdkFlavor : CPythonSdkFlavor<HatchSdkFlavorData>() {
  override fun getIcon(): Icon = PythonHatchIcons.Logo
  override fun getFlavorDataClass(): Class<HatchSdkFlavorData> = HatchSdkFlavorData::class.java
  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean = false
  override fun isPlatformIndependent(): Boolean = true
}