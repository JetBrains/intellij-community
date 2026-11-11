// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PySdkUsage")

package com.jetbrains.python.sdk

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.python.pyproject.model.evolution.EvoPyProjectModel
import com.intellij.python.sdk.backend.isFor
import org.jetbrains.annotations.ApiStatus

/** Whether a Python project of this snapshot uses [sdk]. */
@ApiStatus.Internal
fun EvoPyProjectModel.Snapshot.usesSdk(sdk: Sdk): Boolean = interpreters.any { it.isFor(sdk) }

