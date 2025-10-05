// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.icons.AllIcons
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.python.PyBundle
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isVirtualEnv
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.PythonSdkUtil.isRemote
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

val noInterpreterMarker: String = "<${PyBundle.message("python.sdk.there.is.no.interpreter")}>"

@ApiStatus.Internal

fun name(sdk: Sdk): Triple<String?, String, String?> = name(sdk, sdk.name)

/**
 * Returns modifier that shortly describes that is wrong with passed [sdk], [name] and additional info.
 */
@ApiStatus.Internal

fun name(sdk: Sdk, name: String): Triple<String?, String, String?> {
  val modifier = when {
    !sdk.sdkSeemsValid || PythonSdkType.hasInvalidRemoteCredentials(sdk) -> "invalid"
    PythonSdkType.isIncompleteRemote(sdk) -> "incomplete"
    !LanguageLevel.SUPPORTED_LEVELS.contains(PySdkUtil.getLanguageLevelForSdk(sdk)) -> "unsupported"
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
@ApiStatus.Internal

fun path(sdk: Sdk): @NlsSafe String? {
  val name = sdk.name
  val homePath = sdk.homePath ?: return null

  if (sdk.isTargetBased()) {
    return homePath
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
 * @see sdkSeemsValid
 * @see PythonSdkType.isIncompleteRemote
 * @see PythonSdkType.hasInvalidRemoteCredentials
 * @see LanguageLevel.SUPPORTED_LEVELS
 */
@ApiStatus.Internal

fun icon(sdk: Sdk): Icon {
  val flavor: PythonSdkFlavor<*> = sdk.getOrCreateAdditionalData().flavor

  val icon = flavor.icon

  val providedIcon = PySdkProvider.EP_NAME.extensions.firstNotNullOfOrNull { it.getSdkIcon(sdk) }

  return when {
    (!sdk.sdkSeemsValid) ||
    PythonSdkType.isIncompleteRemote(sdk) ||
    PythonSdkType.hasInvalidRemoteCredentials(sdk) ||
    !LanguageLevel.SUPPORTED_LEVELS.contains(PySdkUtil.getLanguageLevelForSdk(sdk)) ->
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
@ApiStatus.Internal

fun groupModuleSdksByTypes(allSdks: List<Sdk>, module: Module?, invalid: (Sdk) -> Boolean): Map<PyRenderedSdkType, List<Sdk>> {
  return allSdks
    .asSequence()
    .filter { !it.isAssociatedWithAnotherModule(module) && !invalid(it) }
    .groupBy {
      when {
        it.isVirtualEnv || it.isCondaVirtualEnv -> PyRenderedSdkType.VIRTUALENV
        isRemote(it) -> PyRenderedSdkType.REMOTE
        else -> PyRenderedSdkType.SYSTEM
      }
    }
}

/**
 * Order is important, sdks are rendered in the same order as the types are defined.
 *
 * @see groupModuleSdksByTypes
 */
@ApiStatus.Internal

enum class PyRenderedSdkType {
  VIRTUALENV, SYSTEM, REMOTE
}

private fun wrapIconWithWarningDecorator(icon: Icon): LayeredIcon =
  LayeredIcon(2).apply {
    setIcon(icon, 0)
    setIcon(AllIcons.Actions.Cancel, 1)
  }

internal fun SimpleColoredComponent.customizeWithSdkValue(
  value: Any?,
  nullSdkName: @Nls String,
  nullSdkValue: Sdk?,
  actualSdkName: String? = null,
) {
  when (value) {
    is PySdkToInstall -> {
      value.renderInList(this)
    }
    is Sdk -> {
      appendName(value, name(value, actualSdkName ?: value.name))
      icon = icon(value)
    }
    is String -> append(value)
    null -> {
      if (nullSdkValue != null) {
        appendName(nullSdkValue, name(nullSdkValue, nullSdkName))
        icon = icon(nullSdkValue)
      }
      else {
        append(nullSdkName)
      }
    }
  }
}

private fun SimpleColoredComponent.appendName(sdk: Sdk, name: Triple<String?, String, String?>) {
  val (modifier, primary, secondary) = name
  if (modifier != null) {
    append("[$modifier] $primary", SimpleTextAttributes.ERROR_ATTRIBUTES)
  }
  else {
    append(primary)
  }

  if (secondary != null) {
    append(" $secondary", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  }

  path(sdk)?.let { append(" $it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }
}