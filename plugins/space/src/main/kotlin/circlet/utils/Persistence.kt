package circlet.utils

import com.intellij.credentialStore.*
import com.intellij.ide.passwordSafe.*
import runtime.*

object IdeaPersistence : Persistence {
    override suspend fun put(key: String, value: String) {
        PasswordSafe.getInstance().setPassword(createCredentialAttributes(key), value)
    }

    override suspend fun get(key: String): String? =
        PasswordSafe.getInstance().getPassword(createCredentialAttributes(key))

    override suspend fun delete(key: String) {
        PasswordSafe.getInstance().setPassword(createCredentialAttributes(key), null)
    }

    override suspend fun clear() {
    }

    private fun createCredentialAttributes(key: String) =
        CredentialAttributes("${javaClass.name}-$key")
}
