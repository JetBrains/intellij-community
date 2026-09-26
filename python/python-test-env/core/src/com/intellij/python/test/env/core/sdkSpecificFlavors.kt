package com.intellij.python.test.env.core

import com.intellij.openapi.util.SystemInfoRt.isLinux
import com.intellij.openapi.util.SystemInfoRt.isMac
import com.intellij.openapi.util.SystemInfoRt.isWindows
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.MacPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.WinPythonSdkFlavor
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass


@get:ApiStatus.Internal
val osSpecificSdkFlavorAndData: PyFlavorAndData<*, out CPythonSdkFlavor<*>>
  get() = when {
    isMac -> PyFlavorAndData(PyFlavorData.Empty, MacPythonSdkFlavor.getInstance())
    isWindows -> PyFlavorAndData(PyFlavorData.Empty, WinPythonSdkFlavor.getInstance())
    isLinux -> PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance())
    else -> error("Current OS not supported")
  }

// CPython (vanilla) flavor
@get:ApiStatus.Internal
val osSpecificSdkFlavorClass: KClass<out CPythonSdkFlavor<*>> get() = osSpecificSdkFlavorAndData.flavor::class
