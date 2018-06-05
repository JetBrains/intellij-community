package circlet.components

import circlet.app.*
import circlet.client.api.*
import circlet.utils.*
import com.intellij.openapi.components.*
import runtime.reactive.*
import runtime.utils.*

class ConnectionsComponent : ApplicationComponent, LifetimedComponent by SimpleLifetimedComponent() {
    private val connections = LifetimedValueCache<String, LoginModel>(lifetime) { url, connectionLifetime ->
        LoginModel(
            persistence = IdeaPersistence.substorage("$url-"),
            server = url,
            appLifetime = connectionLifetime,
            notificationKind = NotificationKind.Ide
        )
    }

    fun get(url: String, urlLifetime: Lifetime): LifetimedValue<LoginModel> =
        connections.get(url, urlLifetime)
}

val connections: ConnectionsComponent = application.getComponent()
