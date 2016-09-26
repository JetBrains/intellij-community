package circlet.utils

import com.intellij.openapi.application.*
import com.intellij.openapi.components.*

inline fun <reified T: Any> ComponentManager.getComponent() : T =
    this.getComponent(T::class.java) ?: throw Error("Component ${T::class.java} not found in container $this")

val application: Application get() = ApplicationManager.getApplication()

inline fun <reified T : Any>component() : T = application.getComponent<T>()
