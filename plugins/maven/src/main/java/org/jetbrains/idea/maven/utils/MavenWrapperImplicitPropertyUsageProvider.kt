// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils

import com.intellij.codeInspection.unused.ImplicitPropertyUsageProvider
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile

class MavenWrapperImplicitPropertyUsageProvider : ImplicitPropertyUsageProvider() {

  override fun isUsed(property: Property): Boolean {

    val file = property.containingFile.virtualFile
    return nameEqual(file, "maven-wrapper.properties") && nameEqual(file?.parent, "wrapper")
           && nameEqual(file?.parent?.parent, ".mvn");
  }

  private fun nameEqual(file: VirtualFile?, name: String): Boolean {
    if (file == null) return false;
    return Comparing.equal(file.name, name, SystemInfo.isFileSystemCaseSensitive)
  }

}