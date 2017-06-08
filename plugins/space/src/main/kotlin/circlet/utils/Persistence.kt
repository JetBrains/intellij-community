package circlet.utils

import circlet.*
import com.intellij.ide.passwordSafe.*
import com.intellij.openapi.project.*
import runtime.*

object IdeaPersistence : Persistence {
    suspend override fun put(key: String, value: String): String {
        PasswordSafe.getInstance().storePassword(ProjectManager.getInstance().defaultProject, this.javaClass, key, value)
        return value
    }

    suspend override fun get(key: String): String? {
        return PasswordSafe.getInstance().getPassword(ProjectManager.getInstance().defaultProject, this.javaClass, key).orEmpty()
    }

    suspend override fun delete(key: String) {
        PasswordSafe.getInstance().removePassword(ProjectManager.getInstance().defaultProject, this.javaClass, key)
    }

    suspend override fun clear() {
    }
}

object SandboxPersistence : Persistence {
    suspend override fun put(key: String, value: String): String {
        return value
    }

    suspend override fun get(key: String): String? = null

    suspend override fun delete(key: String) {
    }

    suspend override fun clear() {
    }
}

