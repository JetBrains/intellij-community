// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import org.jetbrains.annotations.Nls

/**
 * Error string to be used with [Result]
 */
@JvmInline
value class LocalizedErrorString(val text: @Nls String)