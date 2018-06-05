package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import runtime.reactive.*

inline fun <reified T : Any> ComponentManager.getComponent(): T =
    getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

inline fun <reified T : Any> Project.getService(): T = service<T>().checkService(this)

inline fun <reified T : Any> T?.checkService(container: Any): T =
    this ?: throw Error("Service ${T::class.java} not found in container $container")

val application: Application
    get() = ApplicationManager.getApplication()

interface LifetimedComponent : Lifetimed, BaseComponent

class SimpleLifetimedComponent : SimpleLifetimed(), LifetimedComponent {
    override fun disposeComponent() {
        terminateLifetimeSource()
    }
}

interface LifetimedDisposable : Lifetimed, Disposable

class SimpleLifetimedDisposable : SimpleLifetimed(), LifetimedDisposable {
    override fun dispose() {
        terminateLifetimeSource()
    }
}

open class SimpleLifetimed : Lifetimed {
    private val lifetimeSource = LifetimeSource()

    final override val lifetime: Lifetime
        get() = lifetimeSource

    protected fun terminateLifetimeSource() {
        lifetimeSource.terminate()
    }
}

fun Notification.notify(lifetime: Lifetime, project: Project?) {
    lifetime.add { expire() }

    notify(project)
}

inline fun <T : Any, C : ComponentManager> C.computeSafe(crossinline compute: C.() -> T?): T? =
    application.runReadAction(Computable {
        if (isDisposed) null else compute()
    })

