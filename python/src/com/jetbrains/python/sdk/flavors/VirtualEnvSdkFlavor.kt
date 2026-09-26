// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("VirtualEnvSdkFlavor")
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.flavors


@Deprecated("Use com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor instead")
typealias VirtualEnvSdkFlavor = com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor

@Deprecated("Use com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor.getInstance() instead")
fun getInstance(): VirtualEnvSdkFlavor = com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor.getInstance()
