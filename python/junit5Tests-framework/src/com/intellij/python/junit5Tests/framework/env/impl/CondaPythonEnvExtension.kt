package com.intellij.python.junit5Tests.framework.env.impl

import com.intellij.python.junit5Tests.framework.env.CondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.tools.PythonType

/**
 * Mark [PyCondaEnv] param with [CondaEnv] annotation and register this extension with [com.intellij.python.junit5Tests.framework.env.PyEnvTestCaseWithConda]
 */
internal class CondaPythonEnvExtension : PythonEnvExtensionBase<PyCondaEnv, PythonType.Conda>(CondaEnv::class, PythonType.Conda, PyCondaEnv::class)