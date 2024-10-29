// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.Version
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.installer.BinaryInstallation
import com.jetbrains.python.sdk.installer.installBinary
import com.jetbrains.python.sdk.installer.toResourcePreview
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CalledInAny

val LOGGER = Logger.getInstance(PySdkToInstall::class.java)

@CalledInAny
@Internal
fun getSdksToInstall(): List<PySdkToInstall> {
  return PySdkToInstallManager.getAvailableVersionsToInstall().map {
    PySdkToInstall(it.value)
  }
}

@RequiresEdt
@Internal
fun installSdkIfNeeded(sdk: Sdk, module: Module?, existingSdks: List<Sdk>, context: UserDataHolder? = null): Result<Sdk> =
  if (sdk is PySdkToInstall) sdk.install(module) {
    context?.let { detectSystemWideSdks(module, existingSdks, context) } ?: detectSystemWideSdks(module, existingSdks)
  }
  else Result.success(sdk)


/**
 * Generic PySdkToInstall. Compatible with all OS / CpuArch.
 */
@Internal
class PySdkToInstall(val installation: BinaryInstallation)
  : ProjectJdkImpl(installation.release.title, PythonSdkType.getInstance(), "", installation.release.version) {

  /**
   * Customize [renderer], which is typically either [com.intellij.ui.ColoredListCellRenderer] or [com.intellij.ui.ColoredTreeCellRenderer].
   */
  @CalledInAny
  @Internal
  fun renderInList(renderer: SimpleColoredComponent) {
    renderer.append(name)
    val preview = installation.toResourcePreview()
    renderer.append(" ${preview.description}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)  // NON-NLS
    renderer.icon = AllIcons.Actions.Download
  }

  @CalledInAny
  @NlsContexts.DialogMessage
  fun getInstallationWarning(@NlsContexts.Button defaultButtonName: String): String {
    val preview = installation.toResourcePreview()
    val fileSize = StringUtil.formatFileSize(preview.size)
    return HtmlBuilder()
      .append(PyBundle.message("python.sdk.executable.not.found.header"))
      .append(HtmlChunk.tag("ul").children(
        HtmlChunk.tag("li").children(HtmlChunk.raw(
          PyBundle.message("python.sdk.executable.not.found.option.specify.path", HtmlChunk.text("...").bold(), "python.exe"))),
        HtmlChunk.tag("li").children(HtmlChunk.raw(PyBundle.message("python.sdk.executable.not.found.option.download.and.install",
                                                                    HtmlChunk.text(defaultButtonName).bold(), fileSize)))
      )).toString()
  }

  @RequiresEdt
  @Internal
  fun install(module: Module?, systemWideSdksDetector: () -> List<PyDetectedSdk>): Result<PyDetectedSdk> {
    val project = module?.project
    return installBinary(installation, project) {
      PySdkToInstallManager.findInstalledSdk(
        languageLevel = Version.parseVersion(installation.release.version).toLanguageLevel(),
        project = project,
        systemWideSdksDetector = systemWideSdksDetector
      )
    }
  }
}
