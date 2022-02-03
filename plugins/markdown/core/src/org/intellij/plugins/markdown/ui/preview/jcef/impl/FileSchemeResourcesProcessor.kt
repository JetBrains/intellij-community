// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview.jcef.impl

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.isDirectory
import org.intellij.plugins.markdown.ui.preview.PreviewStaticServer
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.notExists

internal class FileSchemeResourcesProcessor: IncrementalDOMBuilder.FileSchemeResourceProcessingStrategy, ResourceProvider {
  private val resources = hashMapOf<String, String>()

  override fun processFileSchemeResource(basePath: Path, originalUri: URI): String? {
    var path = originalUri.path?.takeUnless { it.isEmpty() } ?: return null
    if (SystemInfo.isWindows && path.startsWith("/")) {
      path = path.trimStart('/', '\\')
    }
    val resolvedPath = basePath.resolve(path)
    if (resolvedPath.notExists() || resolvedPath.isDirectory()) {
      return null
    }
    val fixedPath = FileUtil.toSystemIndependentName(resolvedPath.toString())
    return actuallyProcess(fixedPath)
  }

  private fun actuallyProcess(fixedPath: String): String? {
    val key = calculateResourceKey(fixedPath) ?: return null
    resources[key] = fixedPath
    return PreviewStaticServer.getStaticUrl(this, key)
  }

  fun clear() {
    resources.clear()
  }

  private fun calculateResourceKey(fixedPath: String): String? {
    val path = Path.of(fixedPath)
    val name = path.fileName ?: return null
    val hash = StringUtil.toHexString(DigestUtil.md5().digest(fixedPath.toByteArray()))
    return "fileSchemeResource/$hash-$name"
  }

  override fun canProvide(resourceName: String): Boolean {
    return resourceName in resources
  }

  override fun loadResource(resourceName: String): ResourceProvider.Resource? {
    val fixedPath = resources[resourceName] ?: return null
    return ResourceProvider.loadExternalResource(File(fixedPath))
  }
}
