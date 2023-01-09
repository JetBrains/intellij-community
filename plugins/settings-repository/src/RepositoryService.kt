// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.io.URLUtil
import com.intellij.util.io.isDirectory
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.URIish
import org.jetbrains.settingsRepository.git.createBareRepository
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

interface RepositoryService {
  fun checkUrl(uriString: String, project: Project? = null): @NlsContexts.DialogMessage String? {
    val uri = URIish(uriString)
    val isFile = uri.scheme == URLUtil.FILE_PROTOCOL || (uri.scheme == null && uri.host == null)
    return if (isFile) checkFileRepo(uriString, project) else null
  }

  private fun checkFileRepo(url: String, project: Project?): @NlsContexts.DialogMessage String? {
    val suffix = "/${Constants.DOT_GIT}"
    val file = Paths.get(if (url.endsWith(suffix)) url.substring(0, url.length - suffix.length) else url)
    if (file.exists()) {
      if (!file.isDirectory()) {
        return icsMessage("dialog.message.path.is.not.directory")
      }
      else if (isValidRepository(file)) {
        return null
      }
    }
    else if (!file.isAbsolute) {
      return icsMessage("specify.absolute.path.dialog.message")
    }

    if (MessageDialogBuilder
        .yesNo(icsMessage("init.dialog.title"), icsMessage("init.dialog.message", file))
        .yesText(icsMessage("init.dialog.create.button"))
        .ask(project)) {
      return try {
        createBareRepository(file)
        null
      }
      catch (e: IOException) {
        e.message?.let { icsMessage("init.failed.message", it) } ?: icsMessage("init.failed.message.without.details")
      }
    }
    else {
      return ""
    }
  }

  // must be protected, kotlin bug
  fun isValidRepository(file: Path): Boolean
}