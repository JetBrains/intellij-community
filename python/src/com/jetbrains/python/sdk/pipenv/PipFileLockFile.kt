// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pipenv

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.requirements.icons.PythonRequirementsIcons
import com.jetbrains.python.requirements.PyDependenciesFile
import com.jetbrains.python.sdk.associatedModulePath
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

@Internal
const val PIP_FILE: String = "Pipfile"

@Internal
const val PIP_FILE_LOCK: String = "Pipfile.lock"

@Internal
class PipFileLockFile(override val virtualFile: VirtualFile) : PyDependenciesFile {
  override val icon: Icon get() = PythonRequirementsIcons.Requirements
}


internal fun Sdk.findPipFileLockFile(): PipFileLockFile? {
  val virtualFile = associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it)?.findChild(PIP_FILE_LOCK) }
  return virtualFile?.let { PipFileLockFile(it) }
}