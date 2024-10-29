// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import org.jetbrains.idea.svn.status.Status
import kotlin.jvm.Throws

class CombinedStatusReceiver(val delegates: List<StatusReceiver>) : StatusReceiver {
  @Throws(SvnBindException::class)
  override fun process(path: FilePath, status: Status?) {
    delegates.forEach { it.process(path, status) }
  }

  override fun processIgnored(path: FilePath) {
    delegates.forEach { it.processIgnored(path) }
  }

  override fun processUnversioned(path: FilePath) {
    delegates.forEach { it.processUnversioned(path) }
  }

  override fun processCopyRoot(file: VirtualFile, url: Url?, format: WorkingCopyFormat, rootURL: Url?) {
    delegates.forEach { it.processCopyRoot(file, url, format, rootURL) }
  }

  override fun bewareRoot(vf: VirtualFile, url: Url?) {
    delegates.forEach { it.bewareRoot(vf, url) }
  }

  override fun finish() {
    delegates.forEach { it.finish() }
  }
}