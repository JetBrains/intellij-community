// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.pip

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class PipRepositoryManager(project: Project, sdk: Sdk) : PipBasedRepositoryManager(project, sdk)