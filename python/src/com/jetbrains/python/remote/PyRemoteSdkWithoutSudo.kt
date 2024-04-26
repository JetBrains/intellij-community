// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import java.lang.IllegalStateException

/**
 * This is ugly but necessary workaround. Sometimes we should't execute remote
 * python scripts with sudo even if user wants it.
 *
 * For example, './manage.py startapp' from Django creates new files and directories.
 * If runs with sudo then files will be owned by root. Such behavior will break
 * WebDeployment plugin because it uses SFTP as a backend and SFTP always tries
 * to do changes with user privileges.
 */
class PyRemoteSdkWithoutSudo(private val forward: Sdk) : Sdk by forward {
  override fun getSdkAdditionalData(): SdkAdditionalData? {
    return forward.sdkAdditionalData?.let {
      if (it is PyRemoteSdkAdditionalDataBase) PyRemoteSdkAdditionalDataWithoutSudo(it) else it
    }
  }

  companion object {
    @JvmStatic
    fun wrapNullable(sdk: Sdk?): Sdk? = sdk?.let { PyRemoteSdkWithoutSudo(it) }
  }
}

class PyRemoteSdkAdditionalDataWithoutSudo(forward: PyRemoteSdkAdditionalDataBase) :
  PyRemoteSdkAdditionalDataBase by forward {
  override fun isRunAsRootViaSudo(): Boolean {
    return false
  }

  override fun setRunAsRootViaSudo(runAsRootViaSudo: Boolean) {
    throw IllegalStateException("Tried to set runAsRootViaSudo in ${javaClass}")
  }
}
