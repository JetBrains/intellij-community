// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython

import com.intellij.python.community.services.internal.impl.PythonWithLanguageLevelImpl
import com.intellij.python.community.services.shared.PythonWithLanguageLevel

/**
 * Python installed on OS.
 * [pythonBinary] is guaranteed to be usable and have [languageLevel] at the moment of instance creation.
 *
 * Instances could be obtained with [SystemPythonService]
 */
@JvmInline
value class SystemPython internal constructor(private val impl: PythonWithLanguageLevelImpl) : PythonWithLanguageLevel by impl
