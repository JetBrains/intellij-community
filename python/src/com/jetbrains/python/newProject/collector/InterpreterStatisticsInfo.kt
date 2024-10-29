// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject.collector

import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterTarget
import com.jetbrains.python.statistics.InterpreterType

// This class will be internal soon, do not use it in third party plugins.
data class InterpreterStatisticsInfo(val type: InterpreterType,
                                     val target: InterpreterTarget,
                                     val globalSitePackage: Boolean = false,
                                     val makeAvailableToAllProjects: Boolean = false,
                                     val previouslyConfigured: Boolean = false,
                                     val isWSLContext: Boolean = false,
                                     val creationMode: InterpreterCreationMode = InterpreterCreationMode.NA)