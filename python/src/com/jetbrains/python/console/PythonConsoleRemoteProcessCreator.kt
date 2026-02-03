// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.remote.CredentialsType

interface PythonConsoleRemoteProcessCreator<T> {
  val credentialsType: CredentialsType<T>

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<PythonConsoleRemoteProcessCreator<Any>> = ExtensionPointName.create(
      "Pythonid.remoteConsoleProcessCreator")
  }
}

