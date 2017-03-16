package circlet.utils

import circlet.*
import com.intellij.ide.passwordSafe.*
import com.intellij.openapi.project.*
import runtime.*

object IdeaPersistence : Persistence {

    override fun put(key: String, value: String) : Promise<String> {
        PasswordSafe.getInstance().storePassword(ProjectManager.getInstance().defaultProject, this.javaClass, key, value)
        return Promises.successful(value)
    }

    override fun get(key: String) : Promise<String?> {
        return Promises.successful(PasswordSafe.getInstance().getPassword(ProjectManager.getInstance().defaultProject, this.javaClass, key).orEmpty())
    }


    override fun clear(): Promise<Unit> {
        return Promises.successful(Unit)
    }

}
