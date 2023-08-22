// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageManagerProvider
import com.jetbrains.python.packaging.pipenv.PyPipEnvPackageManager

class PyPipenvPackageManagerProvider : PyPackageManagerProvider {
  override fun tryCreateForSdk(sdk: Sdk): PyPackageManager? = if (sdk.isPipEnv) PyPipEnvPackageManager(sdk) else null
}