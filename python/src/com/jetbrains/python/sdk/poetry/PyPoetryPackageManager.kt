// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.execution.ExecutionException
import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyPackageManagerBridge


internal class PyPoetryPackageManager(sdk: Sdk) : PyPackageManagerBridge(sdk) {
  override fun createVirtualEnv(destinationDir: String, useGlobalSite: Boolean): String {
    throw ExecutionException(PyBundle.message("python.sdk.dialog.message.creating.virtual.environments.based.on.poetry.environments.not.supported"))
  }
}