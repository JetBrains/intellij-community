// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.jetbrains.python.debugger.pydev.tables

import com.intellij.openapi.util.IntellijInternalApi
import com.jetbrains.python.tables.TableCommandParameters

class PyDevImageCommandParameters(val offset: Int?, val imageId: String?) : TableCommandParameters
