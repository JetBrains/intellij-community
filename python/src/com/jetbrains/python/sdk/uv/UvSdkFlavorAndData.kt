// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.uv

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.community.impl.uv.common.icons.PythonCommunityImplUVCommonIcons
import com.intellij.remote.RemoteSdkPropertiesPaths
import com.jetbrains.python.sdk.PySdkUtil
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonFlavorProvider
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import org.jdom.Element
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.pathString

class UvSdkAdditionalData : PythonSdkAdditionalData {
  internal val flavorData: UvSdkFlavorData

  constructor(uvWorkingDirectory: Path?, usePip: Boolean, venvPath: Path?, uvPath: Path?) : this(UvSdkFlavorData(uvWorkingDirectory, usePip, venvPath?.pathString, uvPath?.pathString))

  private constructor(flavorData: UvSdkFlavorData) : super(PyFlavorAndData(flavorData, UvSdkFlavor)) {
    this.flavorData = flavorData
  }

  constructor(data: PythonSdkAdditionalData) : super(data) {
    when (data) {
      is UvSdkAdditionalData -> this.flavorData = data.flavorData
      else -> this.flavorData = UvSdkFlavorData(null, false, null, null)
    }
  }

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_UV, "true")

    // keep backward compatibility with old data
    if (flavorData.uvWorkingDirectory?.pathString?.isNotBlank() == true) {
      element.setAttribute(UV_WORKING_DIR, flavorData.uvWorkingDirectory.pathString)
    }

    if (flavorData.usePip) {
      element.setAttribute(USE_PIP, flavorData.usePip.toString())
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
          val uvWorkingDirectory = if (element.getAttributeValue(UV_WORKING_DIR).isNullOrEmpty()) null else Path.of(element.getAttributeValue(UV_WORKING_DIR))
          val usePip = element.getAttributeValue(USE_PIP)?.toBoolean() ?: false
          val venvPath = if (element.getAttributeValue(UV_VENV_PATH).isNullOrEmpty()) null else Path.of(element.getAttributeValue(UV_VENV_PATH))
          val uvPath = if (element.getAttributeValue(UV_TOOL_PATH).isNullOrEmpty()) null else Path.of(element.getAttributeValue(UV_TOOL_PATH))
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

// TODO PY-87712 Move to a separate storage
data class UvSdkFlavorData(
  val uvWorkingDirectory: Path?,
  val usePip: Boolean,
  val venvPath: FullPathOnTarget?,
  val uvPath: FullPathOnTarget?,
) : PyFlavorData {

  override fun prepareTargetCommandLine(sdk: Sdk, targetCommandLineBuilder: TargetedCommandLineBuilder) {
    val interpreterPath = sdk.sdkAdditionalData?.let { (it as? RemoteSdkPropertiesPaths)?.interpreterPath } ?: sdk.homePath
    if (interpreterPath.isNullOrBlank()) {
      throw IllegalArgumentException("Sdk ${sdk} doesn't have interpreter path set")
    }
    targetCommandLineBuilder.setExePath(interpreterPath)
    targetCommandLineBuilder.addEnvironmentVariable("UV_PROJECT_ENVIRONMENT", venvPath)
    if (!PythonSdkUtil.isRemote(sdk)) {
      PySdkUtil.activateVirtualEnv(sdk)
    }
  }
}

object UvSdkFlavor : CPythonSdkFlavor<UvSdkFlavorData>() {
  override fun getIcon(): Icon = PythonCommunityImplUVCommonIcons.UV
  override fun getFlavorDataClass(): Class<UvSdkFlavorData> = UvSdkFlavorData::class.java

  override fun isValidSdkPath(pathStr: String): Boolean {
    return false
  }
}

class UvSdkFlavorProvider : PythonFlavorProvider {
  override fun getFlavor(): PythonSdkFlavor<*> {
    return UvSdkFlavor
  }
}