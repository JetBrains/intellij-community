// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env.debug.tasks

import com.jetbrains.python.testing.PyAbstractTestFactory
import com.jetbrains.python.testing.PyTestFactory
import java.util.Collections

open class PyTestDebuggingTask(relativeTestDataPath: String, scriptName: String, targetName: String? = null) : PyUnitTestDebuggingTask(relativeTestDataPath, scriptName, targetName) {

  constructor(scriptName: String, targetName: String? = null) : this("/debug/unittests_debugging/", scriptName, targetName)

  constructor(scriptName: String) : this("/debug/unittests_debugging/", scriptName, null)

  override fun getRunConfigurationFactoryClass(): Class<out PyAbstractTestFactory<*>?> {
    return PyTestFactory::class.java
  }

  override fun getTags(): Set<String?> {
    return Collections.singleton("pytest")
  }
}