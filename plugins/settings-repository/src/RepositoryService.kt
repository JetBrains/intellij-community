package org.jetbrains.plugins.settingsRepository

import java.awt.Container
import org.eclipse.jgit.lib.Constants
import java.io.File
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.settingsRepository.git.createBareRepository
import java.io.IOException
import org.eclipse.jgit.transport.URIish
import com.intellij.util.io.URLUtil

public trait RepositoryService {
  public fun checkUrl(uriString: String, messageParent: Container? = null): Boolean {
    val uri = URIish(uriString)
    val isFile: Boolean
    if (uri.getScheme() == URLUtil.FILE_PROTOCOL) {
      isFile = true
    }
    else {
      isFile = uri.getScheme() == null && !uriString.startsWith("git@")
    }

    if (messageParent != null && isFile && !checkFileRepo(uriString, messageParent)) {
      return false
    }
    return true
  }

  public fun checkFileRepo(url: String, messageParent: Container): Boolean {
    val suffix = '/' + Constants.DOT_GIT
    val file = File(if (url.endsWith(suffix)) url.substring(0, url.length() - suffix.length()) else url)
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
    else if (!file.isAbsolute()) {
      Messages.showErrorDialog(messageParent, IcsBundle.message("specify.absolute.path.dialog.message"), "")
      return false
    }

    if (Messages.showYesNoDialog(messageParent, IcsBundle.message("init.dialog.message"), IcsBundle.message("init.dialog.title"), Messages.getQuestionIcon()) == Messages.YES) {
      try {
        createBareRepository(file)
        return true
      }
      catch (e: IOException) {
        Messages.showErrorDialog(messageParent, IcsBundle.message("init.failed.message", e.getMessage()), IcsBundle.message("init.failed.title"))
        return false
      }
    }
    else {
      return false
    }
  }

  protected fun isValidRepository(file: File): Boolean
}