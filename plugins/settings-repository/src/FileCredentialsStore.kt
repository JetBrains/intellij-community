package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.util.PasswordUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.IOUtil
import org.eclipse.jgit.transport.URIish

import java.io.*

class FileCredentialsStore : CredentialsStore {
  // we store only one for any URL, don't want to add complexity, OS keychain should be used
  private var credentials: Credentials? = null

  {
    val loginDataFile = getPasswordStorageFile()
    if (loginDataFile.exists()) {
      try {
        var hasErrors = true
        val `in` = DataInputStream(BufferedInputStream(FileInputStream(loginDataFile)))
        try {
          credentials = Credentials(PasswordUtil.decodePassword(IOUtil.readString(`in`)), PasswordUtil.decodePassword(IOUtil.readString(`in`)))
          hasErrors = false
        }
        finally {
          if (hasErrors) {
            //noinspection ResultOfMethodCallIgnored
            loginDataFile.delete()
          }
          `in`.close()
        }
      }
      catch (e: IOException) {
        BaseRepositoryManager.LOG.error(e)
      }
    }
  }

  private fun getPasswordStorageFile() = File(IcsManager.getPluginSystemDir(), ".git_auth")

  override fun get(uri: URIish): Credentials? = credentials

  override fun reset(uri: URIish) {
    if (credentials != null) {
      credentials = Credentials(credentials!!.username, null)
    }
  }

  override fun save(uri: URIish, credentials: Credentials) {
    if (credentials.equals(this.credentials)) {
      return
    }

    this.credentials = credentials

    try {
      val loginDataFile = getPasswordStorageFile()
      FileUtil.createParentDirs(loginDataFile)
      val out = DataOutputStream(BufferedOutputStream(FileOutputStream(loginDataFile)))
      try {
        IOUtil.writeString(PasswordUtil.encodePassword(credentials.username), out)
        IOUtil.writeString(PasswordUtil.encodePassword(credentials.password), out)
      }
      finally {
        out.close()
      }
    }
    catch (e: IOException) {
      BaseRepositoryManager.LOG.error(e)
    }
  }
}