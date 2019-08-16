package circlet.utils

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.*
import runtime.json.*
import runtime.persistence.*
import runtime.reactive.*

object IdeaPasswordSafePersistence : Persistence {
    override suspend fun batchGetJson(keys: List<String>): List<Pair<String, JsonElement?>> {
       return keys.map { it to get(it)?.let { jsonElement(it) } }
    }

    override suspend fun putJson(key: String, value: JsonElement) {
        put(key, value.text())
    }

    override suspend fun batchPutJson(keyValuePairs: List<Pair<String, JsonElement>>) {
        batchPut(keyValuePairs.map { it.first to it.second.text() })
    }

    override suspend fun put(key: String, value: String) {
        PasswordSafe.instance.setPassword(createCredentialAttributes(key), value)
    }

    override suspend fun get(key: String): String? =
        PasswordSafe.instance.getPassword(createCredentialAttributes(key))

    override suspend fun delete(key: String) {
        PasswordSafe.instance.setPassword(createCredentialAttributes(key), null)
    }

    override suspend fun batchPut(keyValuePairs: List<Pair<String, String>>) {
        TODO("not implemented")
    }

    override suspend fun batchGet(keys: List<String>): List<Pair<String, String?>> {
        TODO("not implemented")
    }

    override suspend fun batchDelete(keys: List<String>) {
        TODO("not implemented")
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
