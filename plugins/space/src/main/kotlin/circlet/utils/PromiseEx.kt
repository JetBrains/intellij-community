package circlet.utils

import com.intellij.openapi.application.*
import runtime.*
import runtime.kdata.*

fun<T> Promise<T>.thenLater(lifetime : Lifetime, modalityState: ModalityState = ModalityState.current(), handler: (T) -> Unit): Promise<T> =
    this.then {
        application.invokeLater({
            if (!lifetime.isTerminated)
                handler(it)
        }, modalityState)
    }

fun<T> Promise<T>.failureLater(lifetime : Lifetime, modalityState: ModalityState = ModalityState.current(), handler: (Failure) -> Unit) =
    this.failure {
        if (application.isDispatchThread)
        {
            if (!lifetime.isTerminated)
                handler(it)
        } else {
            application.invokeLater({
                if (!lifetime.isTerminated)
                    handler(it)
            }, modalityState)
        }
    }
