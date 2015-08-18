package org.jetbrains.keychain

import com.intellij.openapi.diagnostic.Logger

val LOG: Logger = Logger.getInstance(javaClass<CredentialsStore>())

public data class Credentials(id: String?, token: String?) {
  public val id: String? = if (id.isNullOrEmpty()) null else id
  public val token: String? = if (token.isNullOrEmpty()) null else token
}

public fun Credentials?.isFulfilled(): Boolean = this != null && id != null && token != null

public interface CredentialsStore {
  public fun get(host: String?, sshKeyFile: String? = null): Credentials?

  public fun save(host: String?, credentials: Credentials, sshKeyFile: String? = null)

  public fun reset(host: String)
}
