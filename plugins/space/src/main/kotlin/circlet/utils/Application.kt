package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import runtime.reactive.*

@Suppress("unused")
inline fun <reified T : Any> component(): T = application.getComponent()
inline fun <reified T : Any> Project.component(): T = getComponent()

inline fun <reified T : Any> ComponentManager.getComponent(): T =
    this.getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

@Suppress("unused")
inline fun <reified T : Any> getService(): T = service<T>().checkService(application)
inline fun <reified T : Any> Project.getService(): T = service<T>().checkService(this)

inline fun <reified T : Any> T?.checkService(container: Any): T =
    this ?: throw Error("Service ${T::class.java} not found in container $container")

val application: Application
    get() = ApplicationManager.getApplication()

fun Disposable.attachLifetime(): Lifetime {
    val lifetime = Lifetime()

    Disposer.register(this, Disposable { lifetime.terminate() })

    return lifetime
}

class LifetimedOnDisposable(disposable: Disposable) : Lifetimed {
    override val lifetime: Lifetime = disposable.attachLifetime()
}

interface LifetimedDisposable : Lifetimed, Disposable

class SimpleLifetimedDisposable : LifetimedDisposable {
    override val lifetime: Lifetime = Lifetime()

    override fun dispose() {
        lifetime.terminate()
    }
}

fun Notification.notify(lifetime: Lifetime, project: Project?) {
    lifetime.add { expire() }
    Notifications.Bus.notify(this, project)
}
