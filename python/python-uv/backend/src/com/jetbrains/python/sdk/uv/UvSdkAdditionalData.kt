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
          val uvWorkingDirectory = if (element.getAttributeValue(UV_WORKING_DIR).isNullOrEmpty()) {
            Path.of("")
          }
          else {
            Path.of(element.getAttributeValue(UV_WORKING_DIR))
          }
          val usePip = element.getAttributeValue(USE_PIP)?.toBoolean()
          val venvPath = element.getAttributeValue(UV_VENV_PATH)?.takeIf { it.isNotBlank() }
          val uvPath = if (element.getAttributeValue(UV_TOOL_PATH).isNullOrEmpty()) null else element.getAttributeValue(UV_TOOL_PATH)
          val legacyFlavorData = UvSdkFlavorData(uvWorkingDirectory, usePip, venvPath, uvPath)
          UvSdkAdditionalData(legacyFlavorData, uvWorkingDirectory).apply {
            load(element)
          }
        }
        else -> null
      }
    }
  }
}