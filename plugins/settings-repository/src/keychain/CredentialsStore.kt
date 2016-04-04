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
package org.jetbrains.keychain

import com.intellij.openapi.diagnostic.Logger

val LOG: Logger = Logger.getInstance(CredentialsStore::class.java)

class Credentials(id: String?, token: String?) {
  val id: String? = if (id.isNullOrEmpty()) null else id
  val token: String? = if (token.isNullOrEmpty()) null else token

  override fun equals(other: Any?): Boolean {
    if (other !is Credentials) return false
    return id == other.id && token == other.token
  }

  override fun hashCode(): Int {
    return (id?.hashCode() ?: 0) * 37 + (token?.hashCode() ?: 0)
  }
}

fun Credentials?.isFulfilled(): Boolean = this != null && id != null && token != null

interface CredentialsStore {
  fun get(host: String?, sshKeyFile: String? = null): Credentials?

  fun save(host: String?, credentials: Credentials, sshKeyFile: String? = null)

  fun reset(host: String)
}
