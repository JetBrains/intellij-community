// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.extensions.jcef

import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import kotlinx.coroutines.runBlocking
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.service.VirtualFileAccessor
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider

internal class ProcessImagesExtension(
  private val baseFile: VirtualFile?,
  private val project: Project?,
) : ResourceProvider, MarkdownBrowserPreviewExtension {
  override val resourceProvider: ResourceProvider = this

  override fun canProvide(resourceName: String): Boolean {
    return hasImageExtension(resourceName)
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    val baseFileId = baseFile?.rpcId() ?: return null
    val projectId = project?.projectId() ?: return null
    val resource = runBlocking {
      VirtualFileAccessor.getInstance().getFileByResourceName(resourceName, baseFileId, projectId)?.virtualFile()
    } ?: return null
    return ResourceProvider.loadExternalResource(resource)
  }

  override fun dispose() = Unit

  private fun hasImageExtension(name: String): Boolean {
    val lowerName = name.lowercase()
    return lowerName.endsWith(".jpeg") ||
           lowerName.endsWith(".jpg") ||
           lowerName.endsWith(".png") ||
           lowerName.endsWith(".gif") ||
           lowerName.endsWith(".bmp") ||
           lowerName.endsWith(".svg") ||
           lowerName.endsWith(".webp") ||
           lowerName.endsWith(".tiff") ||
           lowerName.endsWith(".tif")
  }

  class Provider : MarkdownBrowserPreviewExtension.Provider {
    override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension {
      return ProcessImagesExtension(panel.virtualFile, panel.project)
    }
  }
}