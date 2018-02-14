package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import runtime.reactive.*

inline fun <reified T : Any> component(): T = application.getComponent()
inline fun <reified T : Any> Project.component(): T = getComponent()

inline fun <reified T : Any> ComponentManager.getComponent(): T =
    this.getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

@Suppress("unused")
inline fun <reified T : Any> getService(): T = service<T>().checkService(application)
inline fun <reified T : Any> Project.getService(): T = service<T>().checkService(this)

inline fun <reified T : Any> T?.checkService(container: Any): T =
    this ?: throw Error("Service ${T::class.java} not found in container $container")

@Suppress("unused")
fun createApplicationLifetime(): Lifetime {
    val result = Lifetime()
    application.addApplicationListener(object : ApplicationAdapter() {
        override fun applicationExiting() {
            result.terminate()
        }
    })
    return result
}

val application: Application
    get() = ApplicationManager.getApplication()

fun Disposable.attachLifetime(): Lifetime {
    val defComponent = Lifetime()
    Disposer.register(this, Disposable { defComponent.terminate() })
    return defComponent
}

interface ILifetimedComponent {
    val componentLifetime: Lifetime
}

class LifetimedComponent(project: Project) : ILifetimedComponent {
    private val lifetime: Lifetime = project.attachLifetime()
    override val componentLifetime: Lifetime
        get() = lifetime
}

interface ILifetimedApplicationComponent : Disposable {
    val componentLifetime: Lifetime
}

class LifetimedApplicationComponent : ILifetimedApplicationComponent {
    private val lifetimeDefinition = Lifetime()

    init {
        Disposer.register(application, this)
    }

    override fun dispose() {
        lifetimeDefinition.terminate()
    }

    override val componentLifetime: Lifetime
        get() = lifetimeDefinition
}

fun Notification.notify(lifetime: Lifetime, project: Project?) {
    lifetime.add { expire() }
    Notifications.Bus.notify(this, project)
}
