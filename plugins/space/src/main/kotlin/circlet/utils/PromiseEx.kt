package circlet.utils

import runtime.*
import runtime.lifetimes.*

fun<T> Promise<T>.thenLater(lifetime : Lifetime, handler: (T) -> Unit): Promise<T> =
    this.then {
        application.invokeLater {
            if (!lifetime.isTerminated)
                handler(it)
        }
    }

fun<T> Promise<T>.failureLater(lifetime : Lifetime, handler: (Throwable) -> Unit) =
    this.failure {
        application.invokeLater {
            if (!lifetime.isTerminated)
                handler(it)
        }
    }
