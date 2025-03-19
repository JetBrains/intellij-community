// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef.impl

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.io.File
import java.net.URL

internal class FileSchemeResourcesProcessor(
  private val baseFile: VirtualFile?,
  private val projectRoot: VirtualFile?,
): ResourceProvider {
  override fun canProvide(resourceName: String): Boolean {
    return baseFile != null && projectRoot != null
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    val resource = if (resourceName.startsWith("file:/")) {
      VfsUtil.findFileByIoFile(File(URL(resourceName).path), true)
    } else {
      projectRoot?.findFileByRelativePath(resourceName)
    } ?: return null
    return ResourceProvider.loadExternalResource(resource)
  }
}
