// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData

/**
 * This is an ugly but necessary workaround.
 * Sometimes we shouldn't execute remote Python scripts with sudo even if user wants it.
 *
 * For example, './manage.py startapp' from Django creates new files and directories.
 * If runs with sudo, then root will own files.
 * Such behavior will break the WebDeployment plugin because it uses SFTP as a backend,
 * and SFTP always tries to do changes with user privileges.
 */
class PyRemoteSdkWithoutSudo(private val forward: Sdk) : Sdk by forward {
  override fun getSdkAdditionalData(): SdkAdditionalData? = forward.sdkAdditionalData
}

