// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.utils

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
import libraries.coroutines.extra.Lifetime
import runtime.json.JsonElement
import runtime.json.jsonElement
import runtime.json.text
import runtime.persistence.Persistence

object IdeaPasswordSafePersistence : Persistence {

  override suspend fun putJson(key: String, value: JsonElement) {
    put(key, value.text())
  }

  override suspend fun getJson(key: String): JsonElement? {
    return get(key)?.let { jsonElement(it) }
  }

  override suspend fun put(key: String, value: String) {
    PasswordSafe.instance.setPassword(createCredentialAttributes(key), value)
  }

  override suspend fun get(key: String): String? =
    PasswordSafe.instance.getPassword(createCredentialAttributes(key))

  override suspend fun delete(key: String) {
    PasswordSafe.instance.setPassword(createCredentialAttributes(key), null)
  }

  override fun forEach(lifetime: Lifetime, key: String, callback: (String?) -> Unit) {
    TODO("not implemented")
  }

  override suspend fun getAllKeys(): List<String> {
    TODO("not implemented")
  }

  override suspend fun clear() {
  }

  private fun createCredentialAttributes(key: String) =
    CredentialAttributes("${javaClass.name}-$key")
}
