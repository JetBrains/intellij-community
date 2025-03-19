// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.poetry

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagerProvider

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PyPoetryPackageManagerProvider : PyPackageManagerProvider {
  override fun tryCreateForSdk(sdk: Sdk): PyPackageManager? = if (sdk.isPoetry) PyPoetryPackageManager(sdk) else null
}