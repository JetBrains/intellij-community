// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.python.community.testFramework.testEnv.conda.TypeConda
import com.intellij.python.junit5Tests.framework.env.CondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv

/**
 * Mark [PyCondaEnv] param with [CondaEnv] annotation and register this extension with [com.intellij.python.junit5Tests.framework.env.PyEnvTestCaseWithConda]
 */
internal class CondaPythonEnvExtension : PythonEnvExtensionBase<PyCondaEnv, TypeConda>(CondaEnv::class, TypeConda, PyCondaEnv::class)