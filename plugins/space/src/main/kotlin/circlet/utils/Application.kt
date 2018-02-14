package circlet.utils

import com.intellij.notification.*
import com.intellij.openapi.*
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.project.*
import com.intellij.openapi.util.*
import runtime.reactive.*

inline fun <reified T : Any> ComponentManager.getComponent(): T =
    this.getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

inline fun <reified T : Any> component(): T = application.getComponent()
inline fun <reified T : Any> Project.component(): T = this.getComponent()

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

// Bad inspection Disposable {} != object: Disposable {}
@Suppress("ObjectLiteralToLambda")
fun Disposable.attachLifetime(): Lifetime {
    val defComponent = Lifetime()
    Disposer.register(this, object : Disposable {
        override fun dispose() {
            defComponent.terminate()
        }
    })
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
