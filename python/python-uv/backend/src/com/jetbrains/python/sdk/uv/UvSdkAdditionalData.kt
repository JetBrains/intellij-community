package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@PyInternalExecApi
class UvSdkAdditionalData : PythonSdkAdditionalData {
  val flavorData: UvSdkFlavorData
    get() = flavorAndData.data as UvSdkFlavorData

  constructor(
    uvWorkingDirectory: Path,
    usePip: Boolean?,
    venvPath: FullPathOnTarget?,
    uvPath: FullPathOnTarget?,
  ) : this(UvSdkFlavorData(uvWorkingDirectory, usePip, venvPath, uvPath), uvWorkingDirectory)

  private constructor(
    legacyFlavorData: UvSdkFlavorData, uvWorkingDirectory: Path,
  ) : super(PyFlavorAndData(legacyFlavorData, UvSdkFlavor), uvWorkingDirectory)

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_UV, "true")

    val persistedWorkingDirectory = workingDirectory.takeIf { hasValidWorkingDirectory() } ?: flavorData.uvWorkingDirectory
    if (persistedWorkingDirectory != null) {
      element.setAttribute(UV_WORKING_DIR, persistedWorkingDirectory.pathString)
    }

    if (flavorData.usePip == true) {
      element.setAttribute(USE_PIP, true.toString())
    }

    if (flavorData.venvPath?.isNotBlank() == true) {
      element.setAttribute(UV_VENV_PATH, flavorData.venvPath)
    }

    if (flavorData.uvPath?.isNotBlank() == true) {
      element.setAttribute(UV_TOOL_PATH, flavorData.uvPath)
    }
  }

  override fun load(element: Element?) {
    super.load(element)
    if (element == null) return

    val legacyFlavorData = getLegacyFlavorData(element)
    val canonicalFlavorData = flavorAndData.data as? UvSdkFlavorData
    val effectiveFlavorData = UvSdkFlavorData(
      uvWorkingDirectory = canonicalFlavorData?.uvWorkingDirectory ?: legacyFlavorData.uvWorkingDirectory,
      usePip = canonicalFlavorData?.usePip ?: legacyFlavorData.usePip,
      venvPath = canonicalFlavorData?.venvPath ?: legacyFlavorData.venvPath,
      uvPath = canonicalFlavorData?.uvPath ?: legacyFlavorData.uvPath,
    )
    if (flavorAndData.flavor != UvSdkFlavor || canonicalFlavorData != effectiveFlavorData) {
      setFlavorAndDataFromLegacy(PyFlavorAndData(effectiveFlavorData, UvSdkFlavor))
    }
    if (canonicalFlavorData?.hasConflictsWith(legacyFlavorData) == true) {
      markMigrationRequired()
    }
  }

  companion object {
    private const val IS_UV = "IS_UV"
    private const val UV_WORKING_DIR = "UV_WORKING_DIR"
    private const val USE_PIP = "USE_PIP"
    private const val UV_VENV_PATH = "UV_VENV_PATH"
    private const val UV_TOOL_PATH = "UV_TOOL_PATH"

    @JvmStatic
    fun load(element: Element): UvSdkAdditionalData? {
      return when {
        element.getAttributeValue(IS_UV) == "true" -> {
          val legacyFlavorData = getLegacyFlavorData(element)
          UvSdkAdditionalData(legacyFlavorData, legacyFlavorData.uvWorkingDirectory ?: Path.of("")).apply {
            load(element)
          }
        }
        else -> null
      }
    }

    private fun getLegacyFlavorData(element: Element): UvSdkFlavorData = UvSdkFlavorData(
      uvWorkingDirectory = element.getAttributeValue(UV_WORKING_DIR)?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
      usePip = element.getAttributeValue(USE_PIP)?.toBoolean(),
      venvPath = element.getAttributeValue(UV_VENV_PATH)?.takeIf { it.isNotBlank() },
      uvPath = element.getAttributeValue(UV_TOOL_PATH)?.takeIf { it.isNotBlank() },
    )

    private fun UvSdkFlavorData.hasConflictsWith(legacyData: UvSdkFlavorData): Boolean =
      valuesConflict(uvWorkingDirectory, legacyData.uvWorkingDirectory) ||
      valuesConflict(usePip, legacyData.usePip) ||
      valuesConflict(venvPath, legacyData.venvPath) ||
      valuesConflict(uvPath, legacyData.uvPath)

    private fun <T : Any> valuesConflict(canonicalValue: T?, legacyValue: T?): Boolean =
      canonicalValue != null && legacyValue != null && canonicalValue != legacyValue
  }
}
