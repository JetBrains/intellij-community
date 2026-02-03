// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Unmodifiable

// Notes (please do not remove):
// - If you think you want to add library roots here: Consider extending file community/python/helpers/syspath.py instead.
// - If used, this file needs to be added to community/python/python-psi-impl/resources/intellij.python.psi.impl.xml.
@Suppress("unused")
internal class PyAdditionalLibraryRootsProvider : AdditionalLibraryRootsProvider() {
  override fun getRootsToWatch(project: Project): @Unmodifiable Collection<VirtualFile> {
    return emptyList()
  }

  override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
    return emptyList()
  }
}
