// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class PipRepositoryManager(
  override val project: Project,
  @Deprecated("Don't use sdk from here") override val sdk: Sdk,
) : PipBasedRepositoryManager()