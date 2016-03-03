/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.intellij.openapi.ui.Messages
import com.intellij.util.exists
import com.intellij.util.io.URLUtil
import com.intellij.util.isDirectory
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.URIish
import org.jetbrains.settingsRepository.git.createBareRepository
import java.awt.Container
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

interface RepositoryService {
  fun checkUrl(uriString: String, messageParent: Container? = null): Boolean {
    val uri = URIish(uriString)
    val isFile: Boolean
    if (uri.scheme == URLUtil.FILE_PROTOCOL) {
      isFile = true
    }
    else {
      isFile = uri.scheme == null && uri.host == null
    }

    if (messageParent != null && isFile && !checkFileRepo(uriString, messageParent)) {
      return false
    }
    return true
  }

  fun checkFileRepo(url: String, messageParent: Container): Boolean {
    val suffix = "/${Constants.DOT_GIT}"
    val file = Paths.get(if (url.endsWith(suffix)) url.substring(0, url.length - suffix.length) else url)
    if (file.exists()) {
      if (!file.isDirectory()) {
        //noinspection DialogTitleCapitalization
        Messages.showErrorDialog(messageParent, "Specified path is not a directory", "Specified Path is Invalid")
        return false
      }
      else if (isValidRepository(file)) {
        return true
      }
    }
    else if (!file.isAbsolute) {
      Messages.showErrorDialog(messageParent, icsMessage("specify.absolute.path.dialog.message"), "")
      return false
    }

    if (Messages.showYesNoDialog(messageParent, icsMessage("init.dialog.message"), icsMessage("init.dialog.title"), Messages.getQuestionIcon()) == Messages.YES) {
      try {
        createBareRepository(file)
        return true
      }
      catch (e: IOException) {
        Messages.showErrorDialog(messageParent, icsMessage("init.failed.message", e.message), icsMessage("init.failed.title"))
        return false
      }
    }
    else {
      return false
    }
  }

  // must be protected, kotlin bug
  fun isValidRepository(file: Path): Boolean
}