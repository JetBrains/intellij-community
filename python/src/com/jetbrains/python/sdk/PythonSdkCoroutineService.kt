// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * Used for CoroutineScope in com.jetbrains.python.sdk
 */
@Service(Service.Level.APP)
internal class PythonSdkCoroutineService(val cs: CoroutineScope)