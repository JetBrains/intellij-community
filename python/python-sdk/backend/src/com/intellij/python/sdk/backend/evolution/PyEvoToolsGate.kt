// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.sdk.backend.evolution

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.jetbrains.python.sdk.eelNativeMode
import org.jetbrains.annotations.ApiStatus

/**
 * Whether the widget offers the tools and their actions for a project on [descriptor].
 *
 * The tools do not work on a non-local eel yet, so there only "Associated", "Custom", "Settings" and "Manage Packages"
 * stay, unless [eelNativeMode] is on (PY-93438).
 *
 * Answered on the backend, which is the only side that knows the project's eel: the frontend's own machine is the
 * user's laptop even when the project lives on an SSH host. The answer travels to the popup as
 * [com.intellij.python.sdk.common.evolution.EvoPyProjectDto.toolsEnabled] (PY-94224).
 */
@ApiStatus.Internal
fun areToolsEnabledFor(descriptor: EelDescriptor): Boolean = descriptor == LocalEelDescriptor || eelNativeMode
