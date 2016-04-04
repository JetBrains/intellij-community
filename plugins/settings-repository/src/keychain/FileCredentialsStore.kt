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

import com.intellij.openapi.util.PasswordUtil
import com.intellij.util.delete
import com.intellij.util.exists
import com.intellij.util.inputStream
import com.intellij.util.io.IOUtil
import com.intellij.util.outputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Path

class FileCredentialsStore(private val storeFile: Path) : CredentialsStore {
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
        val `in` = DataInputStream(storeFile.inputStream().buffered())
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
      val out = DataOutputStream(storeFile.outputStream().buffered())
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