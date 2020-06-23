package circlet.utils

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.*
import libraries.coroutines.extra.*
import runtime.json.*
import runtime.persistence.*

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
