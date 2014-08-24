package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.PasswordUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.IOUtil
import org.eclipse.jgit.transport.URIish

import java.io.*

public class FileCredentialsStore : CredentialsStore {
  // we store only one pair for any URL, don't want to add complexity, OS keychain should be used
  private var credentials: CredentialsStore.Credentials? = null

  {
    val loginDataFile = getPasswordStorageFile()
    if (loginDataFile.exists()) {
      try {
        var hasErrors = true
        val `in` = DataInputStream(FileInputStream(loginDataFile))
        try {
          credentials = CredentialsStore.Credentials(PasswordUtil.decodePassword(IOUtil.readString(`in`)), PasswordUtil.decodePassword(IOUtil.readString(`in`)))
          hasErrors = false
        } finally {
          if (hasErrors) {
            //noinspection ResultOfMethodCallIgnored
            loginDataFile.delete()
          }
          `in`.close()
        }
      } catch (e: IOException) {
        BaseRepositoryManager.LOG.error(e)
      }

    }
  }

  private fun getPasswordStorageFile(): File {
    return File(IcsManager.getPluginSystemDir(), ".git_auth")
  }

  override fun get(uri: URIish): CredentialsStore.Credentials? {
    return credentials
  }

  override fun reset(uri: URIish) {
    credentials!!.password = null
  }

  override fun save(uri: URIish, credentials: CredentialsStore.Credentials) {
    this.credentials = credentials
    ApplicationManager.getApplication()!!.executeOnPooledThread(object : Runnable {
      override fun run() {
        try {
          val loginDataFile = getPasswordStorageFile()
          FileUtil.createParentDirs(loginDataFile)
          val out = DataOutputStream(FileOutputStream(loginDataFile))
          try {
            IOUtil.writeString(PasswordUtil.encodePassword(credentials.username), out)
            IOUtil.writeString(PasswordUtil.encodePassword(credentials.password), out)
          } finally {
            out.close()
          }
        } catch (e: IOException) {
          BaseRepositoryManager.LOG.error(e)
        }

      }
    })
  }
}