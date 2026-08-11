package com.jetbrains.python.hatch.sdk

import com.intellij.python.hatch.impl.sdk.HatchSdkFlavor
import com.intellij.python.hatch.impl.sdk.HatchSdkFlavorData
import com.jetbrains.python.PyInternalExecApi
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@PyInternalExecApi
class HatchSdkAdditionalData : PythonSdkAdditionalData {
  constructor(hatchWorkingDirectory: Path, hatchEnvironmentName: String?) : this(
    HatchSdkFlavorData(hatchEnvironmentName),
    hatchWorkingDirectory,
  )

  private constructor(flavorData: HatchSdkFlavorData, hatchWorkingDirectory: Path) : super(
    PyFlavorAndData(data = flavorData, flavor = HatchSdkFlavor), hatchWorkingDirectory,
  )

  override fun save(element: Element) {
    super.save(element)
    element.setAttribute(IS_HATCH, "true")
    val data = flavorAndData.data as HatchSdkFlavorData
    val persistedWorkingDirectory = workingDirectory.takeIf { hasValidWorkingDirectory() }
    if (persistedWorkingDirectory != null) {
      element.setAttribute(HATCH_WORKING_DIRECTORY, persistedWorkingDirectory.pathString)
    }
    data.hatchEnvironmentName?.let {
      element.setAttribute(HATCH_ENVIRONMENT_NAME, it)
    }
  }

  override fun load(element: Element?) {
    super.load(element)
    if (element == null) return

    val legacyWorkingDirectory = getWorkingDirectory(element)
    val legacyEnvironmentName = getEnvironmentName(element)

    val canonicalFlavorData = flavorAndData.data as? HatchSdkFlavorData
    val effectiveFlavorData = HatchSdkFlavorData(canonicalFlavorData?.hatchEnvironmentName ?: legacyEnvironmentName)
    if (flavorAndData.flavor != HatchSdkFlavor || canonicalFlavorData != effectiveFlavorData) {
      setFlavorAndDataFromLegacy(PyFlavorAndData(effectiveFlavorData, HatchSdkFlavor))
    }
    if (canonicalFlavorData?.hatchEnvironmentName != null &&
        legacyEnvironmentName != null &&
        canonicalFlavorData.hatchEnvironmentName != legacyEnvironmentName) {
      markMigrationRequired()
    }

    if (legacyWorkingDirectory != null && legacyWorkingDirectory != workingDirectory) {
      markMigrationRequired()
    }
  }

  companion object {
    private const val IS_HATCH = "IS_HATCH"
    private const val HATCH_WORKING_DIRECTORY = "HATCH_WORKING_DIR"
    private const val HATCH_ENVIRONMENT_NAME = "HATCH_ENVIRONMENT_NAME"

    fun createIfHatch(element: Element): HatchSdkAdditionalData? {
      if (element.getAttributeValue(IS_HATCH) != "true") return null

      val workingDirectory = getWorkingDirectory(element) ?: Path.of("")
      val flavorData = HatchSdkFlavorData(getEnvironmentName(element))
      return HatchSdkAdditionalData(flavorData, workingDirectory).apply {
        load(element)
      }
    }

    private fun getEnvironmentName(element: Element) = element.getAttributeValue(HATCH_ENVIRONMENT_NAME)

    private fun getWorkingDirectory(element: Element): Path? =
      element.getAttributeValue(HATCH_WORKING_DIRECTORY)?.takeIf { it.isNotBlank() }?.let { Path.of(it) }
  }
}
