// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands.dto

import java.io.Serializable

data class GradleTaskInfoDto(val taskName: String, val projectHomeDirName: String? = null, val runFromRunAnything: Boolean = false) : Serializable
