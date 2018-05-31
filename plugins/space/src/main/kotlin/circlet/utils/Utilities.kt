package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import com.intellij.xml.util.*
import runtime.reactive.*

inline fun <reified T : Any> ComponentManager.getComponent(): T =
    getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

inline fun <reified T : Any> Project.getService(): T = service<T>().checkService(this)

inline fun <reified T : Any> T?.checkService(container: Any): T =
    this ?: throw Error("Service ${T::class.java} not found in container $container")

val application: Application
    get() = ApplicationManager.getApplication()

fun Disposable.attachLifetime(): Lifetime {
    val lifetime = LifetimeSource()

    Disposer.register(this, Disposable { lifetime.terminate() })

    return lifetime
}

class LifetimedOnDisposable(disposable: Disposable) : Lifetimed {
    override val lifetime: Lifetime = disposable.attachLifetime()
}

interface LifetimedDisposable : Lifetimed, Disposable

class SimpleLifetimedDisposable : LifetimedDisposable {
    private val lifetimeSource = LifetimeSource()

    override val lifetime: Lifetime = lifetimeSource

    override fun dispose() {
        lifetimeSource.terminate()
    }
}

fun Project.notify(lifetime: Lifetime, text: String, handler: (() -> Unit)? = null) {
    notify(lifetime, text, handler?.let { NotificationListener { _, _ -> it() } })
}

fun Project.notify(lifetime: Lifetime, text: String, listener: NotificationListener?) {
    Notification(
        "Circlet",
        "Circlet",
        XmlStringUtil.wrapInHtml(text),
        NotificationType.INFORMATION,
        listener
    ).notify(lifetime, this)
}

fun Notification.notify(lifetime: Lifetime, project: Project?) {
    lifetime.add { expire() }

    notify(project)
}

inline fun <T : Any, C : ComponentManager> C.computeSafe(crossinline compute: C.() -> T?): T? =
    application.runReadAction(Computable {
        if (isDisposed) null else compute()
    })

