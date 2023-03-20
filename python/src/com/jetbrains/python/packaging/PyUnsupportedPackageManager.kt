// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.RemoteFile
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase

class PyUnsupportedPackageManager(sdk: Sdk) : PyPackageManagerImpl(sdk) {

  override fun shouldSubscribeToLocalChanges(): Boolean = false

  override fun getHelperPath(helper: String): String? = (sdk.sdkAdditionalData as? PyRemoteSdkAdditionalDataBase)?.helpersPath?.let {
    RemoteFile(it, helper).path
  }

  override fun getPythonProcessOutput(helperPath: String,
                                      args: MutableList<String>,
                                      askForSudo: Boolean,
                                      showProgress: Boolean,
                                      workingDir: String?,
                                      pyArgs: List<String>?): ProcessOutput {
    if (sdk.homePath == null) {
      throw ExecutionException(PySdkBundle.message("python.sdk.cannot.find.python.interpreter.for.sdk", sdk.name))
    }
    if (sdk.sdkAdditionalData !is PyRemoteSdkAdditionalDataBase) {
      throw PyExecutionException(PySdkBundle.message("python.sdk.invalid.remote.sdk"), helperPath, args)
    }
    throw PyExecutionException(PySdkBundle.message("python.sdk.package.managing.not.supported.for.sdk", sdk.name), helperPath, args)
  }
}