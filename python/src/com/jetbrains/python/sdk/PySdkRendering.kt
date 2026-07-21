// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.jetbrains.python.PyBundle
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isNonToolVirtualEnv
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@Nls
val noInterpreterMarker: String = "<${PyBundle.message("python.sdk.there.is.no.interpreter")}>"


/**
 * Groups valid sdks associated with the [module] by types.
 * Virtual environments, pipenv and conda environments are considered as [PyRenderedSdkType.VIRTUALENV].
 * Remote interpreters are considered as [PyRenderedSdkType.REMOTE].
 * All the others are considered as [PyRenderedSdkType.SYSTEM].
 *
 * @see Sdk.isAssociatedWithAnotherModule
 * @see com.jetbrains.python.sdk.legacy.PythonSdkUtil.isRemote
 * @see PyRenderedSdkType
 */
@ApiStatus.Internal

fun groupModuleSdksByTypes(allSdks: List<Sdk>, module: Module?, invalid: (Sdk) -> Boolean): Map<PyRenderedSdkType, List<Sdk>> {
  return allSdks
    .asSequence()
    .filter { !it.isAssociatedWithAnotherModule(module) && !invalid(it) }
    .groupBy {
      when {
        it.isNonToolVirtualEnv || it.isCondaVirtualEnv -> PyRenderedSdkType.VIRTUALENV
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
      val presentationInfo = value.pyInterpreterPresentation(actualSdkName ?: value.name)
      appendPresentationInfo(presentationInfo)
    }
    is String -> append(value)
    null -> {
      if (nullSdkValue != null) {
        val presentationInfo = nullSdkValue.pyInterpreterPresentation(nullSdkName)
        appendPresentationInfo(presentationInfo)
      }
      else {
        append(nullSdkName)
      }
    }
  }
}

private fun SimpleColoredComponent.appendPresentationInfo(presentationInfo: PythonInterpreterPresentation) = with(presentationInfo) {
  this@appendPresentationInfo.icon = icon
  if (modifier != null) {
    append("[$modifier] $name", SimpleTextAttributes.ERROR_ATTRIBUTES)
  }
  else {
    append(name)
  }

  if (suffix != null) {
    append(" $suffix", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
  }

  append(" $description", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
}