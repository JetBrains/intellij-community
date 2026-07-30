// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements.impl

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.requirements.PyDependenciesFileProvider
import com.jetbrains.python.requirements.RequirementsTxtFile
import com.jetbrains.python.requirements.isRequirementsTxtFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RequirementsTxtFileProvider : PyDependenciesFileProvider {
  override suspend fun fromFile(file: VirtualFile): PyDependenciesFile? =
    if (file.isRequirementsTxtFile()) RequirementsTxtFile(file) else null
}
