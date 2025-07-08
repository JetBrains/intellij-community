// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output.quickfixes

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.Navigatable
import com.intellij.util.SlowOperations
import java.nio.file.Path

class PathNavigatable(private val myProject: Project, private val myPath: Path, private val myOffset: Int) : Navigatable {

  private val myDescriptor by lazy(::createFileDescriptor)

  override fun navigate(requestFocus: Boolean) {
    val descriptor = SlowOperations.knownIssue("IJPL-162975").use { ignore ->
      myDescriptor
    }
    descriptor?.navigate(requestFocus)
  }

  private fun createFileDescriptor(): OpenFileDescriptor? {
    val vFile = VfsUtil.findFile(myPath, false) ?: return null
    return OpenFileDescriptor(myProject, vFile, myOffset)
  }

  override fun canNavigate(): Boolean {
    return myDescriptor?.canNavigate() ?: false
  }

  override fun canNavigateToSource(): Boolean {
    return myDescriptor?.canNavigateToSource() ?: false
  }
}