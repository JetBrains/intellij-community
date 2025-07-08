package com.jetbrains.python.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Each [Sdk] has [PythonSdkAdditionalData]. Use this method to get it.
 * Although each SDK should already have one, some old may lack it.
 *
 * This method creates new in this case, but only if an SDK flavor doesn't require special additional data.
 */
@Internal

fun Sdk.getOrCreateAdditionalData(): PythonSdkAdditionalData {
  val existingData = sdkAdditionalData as? PythonSdkAdditionalData
  if (existingData != null) {
    return existingData
  }

  if (homePath == null) {
    error("homePath is null for $this")
  }

  val flavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(homePath!!)
  if (flavor == null) {
    error("No flavor detected for $homePath sdk")
  }

  val newData = PythonSdkAdditionalData(if (flavor.supportsEmptyData()) flavor else null)
  val modificator = sdkModificator
  modificator.sdkAdditionalData = newData
  val application = ApplicationManager.getApplication()
  if (application.isDispatchThread) {
    application.runWriteAction { modificator.commitChanges() }
  }
  else {
    application.invokeLater {
      application.runWriteAction { modificator.commitChanges() }
    }
  }
  return newData
}

/**
 * Saves SDK to the project table if there is no sdk with same name
 */

@Internal
suspend fun Sdk.persist(): Unit = edtWriteAction {
  if (ProjectJdkTable.getInstance().findJdk(name) == null) { // Saving 2 SDKs with same name is an error
    getOrCreateAdditionalData() // additional data is always required
    ProjectJdkTable.getInstance().addJdk(this)
  }
}

