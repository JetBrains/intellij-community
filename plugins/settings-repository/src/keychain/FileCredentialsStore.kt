package org.jetbrains.keychain

import com.intellij.openapi.util.PasswordUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.IOUtil
import java.io.*

class FileCredentialsStore(private val storeFile: File) : CredentialsStore {
  // we store only one for any URL, don't want to add complexity, OS keychain should be used
  private var credentials: Credentials? = null

  private var dataLoaded = !storeFile.exists()

  private fun ensureLoaded() {
    if (dataLoaded) {
      return
    }

    dataLoaded = true
    if (storeFile.exists()) {
      try {
        var hasErrors = true
        val `in` = DataInputStream(FileInputStream(storeFile).buffered())
        try {
          credentials = Credentials(PasswordUtil.decodePassword(IOUtil.readString(`in`)), PasswordUtil.decodePassword(IOUtil.readString(`in`)))
          hasErrors = false
        }
        finally {
          if (hasErrors) {
            //noinspection ResultOfMethodCallIgnored
            storeFile.delete()
          }
          `in`.close()
        }
      }
      catch (e: IOException) {
        LOG.error(e)
      }
    }
  }

  override fun get(host: String?, sshKeyFile: String?): Credentials? {
    ensureLoaded()
    return credentials
  }

  override fun reset(host: String) {
    if (credentials != null) {
      dataLoaded = true
      storeFile.delete()

      credentials = Credentials(credentials!!.id, null)
    }
  }

  override fun save(host: String?, credentials: Credentials, sshKeyFile: String?) {
    if (credentials.equals(this.credentials)) {
      return
    }

    this.credentials = credentials

    try {
      FileUtil.createParentDirs(storeFile)
      val out = DataOutputStream(FileOutputStream(storeFile).buffered())
      try {
        IOUtil.writeString(PasswordUtil.encodePassword(credentials.id), out)
        IOUtil.writeString(PasswordUtil.encodePassword(credentials.token), out)
      }
      finally {
        out.close()
      }
    }
    catch (e: IOException) {
      LOG.error(e)
    }
  }
}