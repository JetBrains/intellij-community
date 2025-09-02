// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands.dto

import com.intellij.tools.ide.performanceTesting.commands.SdkObject
import java.io.Serializable

data class NewSpringProjectDto(
  val projectName: String,
  val buildType: BuildType,
  val frameworkVersion: String,
  val sdkObject: SdkObject? = null
) : Serializable
