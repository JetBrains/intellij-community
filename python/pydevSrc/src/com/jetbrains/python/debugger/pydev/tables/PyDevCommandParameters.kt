// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.tables

import com.jetbrains.python.tables.TableCommandParameters

class PyDevCommandParameters(val start: Int, val end: Int, val format: String?) : TableCommandParameters
