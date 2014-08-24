package org.jetbrains.plugins.settingsRepository

import com.intellij.openapi.util.text.StringUtil
import org.eclipse.jgit.transport.URIish

public trait CredentialsStore {
  public class Credentials(username: String?, password: String?) {
    public var username: String?
    public var password: String?

    {
      this.username = StringUtil.nullize(username, true)
      this.password = StringUtil.nullize(password)
    }
  }

  public fun get(uri: URIish): Credentials?

  public fun save(uri: URIish, credentials: Credentials)

  public fun reset(uri: URIish)
}