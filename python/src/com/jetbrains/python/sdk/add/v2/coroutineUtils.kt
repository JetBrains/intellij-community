// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

internal fun <T> Flow<T>.logException(logger: Logger): Flow<T> = catch { logger.error(it) }