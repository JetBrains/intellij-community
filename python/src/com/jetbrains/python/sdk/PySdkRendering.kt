// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.LayeredIcon
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import javax.swing.Icon

val noInterpreterMarker: String = "<${PyBundle.message("python.sdk.there.is.no.interpreter")}>"

fun name(sdk: Sdk): Triple<String?, String, String?> = name(sdk, sdk.name)

/**
 * Returns modifier that shortly describes that is wrong with passed [sdk], [name] and additional info.
 */
fun name(sdk: Sdk, name: String): Triple<String?, String, String?> {
  val modifier = when {
    PythonSdkUtil.isInvalid(sdk) || PythonSdkType.hasInvalidRemoteCredentials(sdk) -> "invalid"
    PythonSdkType.isIncompleteRemote(sdk) -> "incomplete"
    !LanguageLevel.SUPPORTED_LEVELS.contains(PythonSdkType.getLanguageLevelForSdk(sdk)) -> "unsupported"
    else -> null
  }
  val providedForSdk = PySdkProvider.EP_NAME.extensions.firstNotNullOfOrNull { it.getSdkAdditionalText(sdk) }

  val secondary = providedForSdk ?: if (PythonSdkType.isRunAsRootViaSudo(sdk)) "[sudo]" else null

  return Triple(modifier, name, secondary)
}

/**
 * Returns a path to be rendered as the sdk's path.
 *
 * Initial value is taken from the [sdk],
 * then it is converted to a path relative to the user home directory.
 *
 * Returns null if the initial path or the relative value are presented in the sdk's name.
 *
 * @see FileUtil.getLocationRelativeToUserHome
 */
fun path(sdk: Sdk): String? {
  val name = sdk.name
  val homePath = sdk.homePath ?: return null

  if (sdk.isTargetBased()) {
    return homePath.removePrefix("target://")
  }

  if (sdk.sdkAdditionalData is PyRemoteSdkAdditionalDataMarker) {
    return homePath.takeIf { homePath !in name }
  }

  return homePath.let { FileUtil.getLocationRelativeToUserHome(it) }.takeIf { homePath !in name && it !in name }
}

/**
 * Returns an icon to be used as the sdk's icon.
 *
 * Result is wrapped with [AllIcons.Actions.Cancel]
 * if the sdk is local and does not exist, or remote and incomplete or has invalid credentials, or is not supported.
 *
 * @see PythonSdkUtil.isInvalid
 * @see PythonSdkType.isIncompleteRemote
 * @see PythonSdkType.hasInvalidRemoteCredentials
 * @see LanguageLevel.SUPPORTED_LEVELS
 */
fun icon(sdk: Sdk): Icon? {
  val flavor: PythonSdkFlavor? = when (sdk.sdkAdditionalData) {
    !is PyRemoteSdkAdditionalDataMarker -> PythonSdkFlavor.getPlatformIndependentFlavor(sdk.homePath)
    else -> null
  }
  val icon = flavor?.icon ?: ((sdk.sdkType as? SdkType)?.icon ?: return null)

  val providedIcon = PySdkProvider.EP_NAME.extensions.firstNotNullOfOrNull { it.getSdkIcon(sdk) }

  return when {
    PythonSdkUtil.isInvalid(sdk) ||
    PythonSdkType.isIncompleteRemote(sdk) ||
    PythonSdkType.hasInvalidRemoteCredentials(sdk) ||
    !LanguageLevel.SUPPORTED_LEVELS.contains(PythonSdkType.getLanguageLevelForSdk(sdk)) ->
      wrapIconWithWarningDecorator(icon)
    sdk is PyDetectedSdk -> IconLoader.getTransparentIcon(icon)
    providedIcon != null -> providedIcon
    else -> icon
  }
}

/**
 * Groups valid sdks associated with the [module] by types.
 * Virtual environments, pipenv and conda environments are considered as [PyRenderedSdkType.VIRTUALENV].
 * Remote interpreters are considered as [PyRenderedSdkType.REMOTE].
 * All the others are considered as [PyRenderedSdkType.SYSTEM].
 *
 * @see Sdk.isAssociatedWithAnotherModule
 * @see PythonSdkUtil.isVirtualEnv
 * @see PythonSdkUtil.isCondaVirtualEnv
 * @see PythonSdkUtil.isRemote
 * @see PyRenderedSdkType
 */
fun groupModuleSdksByTypes(allSdks: List<Sdk>, module: Module?, invalid: (Sdk) -> Boolean): Map<PyRenderedSdkType, List<Sdk>> {
  return allSdks
    .asSequence()
    .filter { !it.isAssociatedWithAnotherModule(module) && !invalid(it) }
    .groupBy {
      when {
        PythonSdkUtil.isVirtualEnv(it) || PythonSdkUtil.isCondaVirtualEnv(it) -> PyRenderedSdkType.VIRTUALENV
        PythonSdkUtil.isRemote(it) -> PyRenderedSdkType.REMOTE
        else -> PyRenderedSdkType.SYSTEM
      }
    }
}

/**
 * Order is important, sdks are rendered in the same order as the types are defined.
 *
 * @see groupModuleSdksByTypes
 */
enum class PyRenderedSdkType {
  VIRTUALENV, SYSTEM, REMOTE
}

private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon =
  LayeredIcon(2).apply {
    setIcon(icon, 0)
    setIcon(AllIcons.Actions.Cancel, 1)
  }
