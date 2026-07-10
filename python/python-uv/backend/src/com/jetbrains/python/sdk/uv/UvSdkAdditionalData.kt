package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
class UvSdkAdditionalData : PythonSdkAdditionalData {
  val flavorData: UvSdkFlavorData

  constructor(uvWorkingDirectory: Path?, usePip: Boolean?, venvPath: FullPathOnTarget?, uvPath: FullPathOnTarget?) : this(UvSdkFlavorData(
    uvWorkingDirectory,
    usePip,
    venvPath,
    uvPath))

  private constructor(flavorData: UvSdkFlavorData) : super(PyFlavorAndData(flavorData, UvSdkFlavor)) {
    this.flavorData = flavorData
  }

  constructor(data: PythonSdkAdditionalData) : super(data) {
    when (data) {
      is UvSdkAdditionalData -> this.flavorData = data.flavorData
      else -> this.flavorData = UvSdkFlavorData(null, null, null, null)
    }
  }

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_UV, "true")

    // keep backward compatibility with old data
    if (flavorData.uvWorkingDirectory?.pathString?.isNotBlank() == true) {
      element.setAttribute(UV_WORKING_DIR, flavorData.uvWorkingDirectory.pathString)
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
          val uvWorkingDirectory =
            if (element.getAttributeValue(UV_WORKING_DIR).isNullOrEmpty()) null else Path.of(element.getAttributeValue(UV_WORKING_DIR))
          val usePip = element.getAttributeValue(USE_PIP)?.toBoolean()
          val venvPath = if (element.getAttributeValue(UV_VENV_PATH).isNullOrEmpty()) null else element.getAttributeValue(UV_VENV_PATH)
          val uvPath = if (element.getAttributeValue(UV_TOOL_PATH).isNullOrEmpty()) null else element.getAttributeValue(UV_TOOL_PATH)
          UvSdkAdditionalData(uvWorkingDirectory, usePip, venvPath, uvPath).apply {
            load(element)
          }
        }
        else -> null
      }
    }

    @JvmStatic
    fun copy(data: PythonSdkAdditionalData): UvSdkAdditionalData {
      return UvSdkAdditionalData(data)
    }
  }
}