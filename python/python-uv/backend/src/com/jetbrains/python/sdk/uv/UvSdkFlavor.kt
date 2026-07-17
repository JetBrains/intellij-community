package com.jetbrains.python.sdk.uv

import com.intellij.python.uv.common.icons.PythonUvCommonIcons
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import javax.swing.Icon

@ApiStatus.Internal
@PyInternalExecApi
object UvSdkFlavor : CPythonSdkFlavor<UvSdkFlavorData>() {
  override fun getIcon(): Icon = PythonUvCommonIcons.UV
  override fun getFlavorDataClass(): Class<UvSdkFlavorData> = UvSdkFlavorData::class.java

  override fun migrateAdditionalData(
    additionalData: PythonSdkAdditionalData,
    data: UvSdkFlavorData,
  ): AdditionalDataMigration<UvSdkFlavorData> {
    val workingDirectory = additionalData.workingDirectory.takeIf { additionalData.hasValidWorkingDirectory() }
                           ?: data.uvWorkingDirectory
    val migratedData = data.copy(
      uvWorkingDirectory = workingDirectory,
      usePip = data.usePip,
      venvPath = data.venvPath,
      uvPath = data.uvPath,
    )
    return AdditionalDataMigration(migratedData, workingDirectory)
  }

  override fun withWorkingDirectory(
    data: UvSdkFlavorData,
    workingDirectory: Path,
  ): UvSdkFlavorData = data.copy(uvWorkingDirectory = workingDirectory)

  override fun isValidSdkPath(pythonBinaryPath: Path): Boolean {
    return false
  }
}