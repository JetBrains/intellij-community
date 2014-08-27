package org.jetbrains.plugins.settingsRepository

import org.eclipse.jgit.transport.URIish
import com.mcdermottroe.apple.OSXKeychain
import com.intellij.openapi.util.PasswordUtil
import gnu.trove.THashMap
import com.mcdermottroe.apple.OSXKeychainException

class OsXCredentialsStore : CredentialsStore {
  class object {
    val SERVICE_NAME = "IDEAPlatformSettingsRepository"
  }

  private val hostToCredentials = THashMap<String, Credentials>()

  override fun get(uri: URIish): Credentials? {
    val host = uri.getHost()!!

    var credentials = hostToCredentials[host]
    if (credentials != null) {
      return credentials
    }

    val data: String?
    try {
      data = OSXKeychain.getInstance().findGenericPassword(SERVICE_NAME, host)
    }
    catch (e: OSXKeychainException) {
      if (e.getMessage()?.contains("The specified item could not be found in the keychain.") === false) {
        IcsManager.LOG.error(e)
      }
      return null
    }

    if (data != null) {
      val separatorIndex = data.indexOf('@')
      if (separatorIndex > 0) {
        val username = PasswordUtil.decodePassword(data.substring(0, separatorIndex))
        val password = PasswordUtil.decodePassword(data.substring(separatorIndex + 1))
        credentials = Credentials(username, password)
        hostToCredentials[host] = credentials!!
      }
    }
    return credentials
  }

  override fun save(uri: URIish, credentials: Credentials) {
    val host = uri.getHost()!!

    var oldCredentials = hostToCredentials.put(host, credentials)
    if (credentials.equals(oldCredentials)) {
      return
    }

    val data = "${PasswordUtil.encodePassword(credentials.username)}@${PasswordUtil.encodePassword(credentials.password)}"

    val keychain = OSXKeychain.getInstance()
    if (oldCredentials == null) {
      try {
        keychain.addGenericPassword(SERVICE_NAME, host, data)
        return
      }
      catch(e: OSXKeychainException) {
        if (e.getMessage()?.contains("The specified item already exists in the keychain.") !== true) {
          throw e
        }
      }
    }

    keychain.modifyGenericPassword(SERVICE_NAME, host, data)
  }

  override fun reset(uri: URIish) {
    val host = uri.getHost()!!
    if (hostToCredentials.remove(host) != null) {
      OSXKeychain.getInstance().deleteGenericPassword(SERVICE_NAME, host)
    }
  }
}