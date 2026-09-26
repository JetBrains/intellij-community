// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SpellCheckingInspection")
package com.jetbrains.python.debugger

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class PyDebuggerBackend { PYDEVD, DEBUGPY }
