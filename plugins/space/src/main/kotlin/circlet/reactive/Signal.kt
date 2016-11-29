package circlet.reactive

import klogging.*
import runtime.collections.*
import runtime.klogger.*
import runtime.lifetimes.*
import java.util.concurrent.atomic.AtomicReference

private val log = KLoggers.logger("app-idea/Signal.kt")

class Signal<T> : ISignal<T>
{
    private var listeners = AtomicReference<Array<(T) -> Unit>>(emptyArray())

    override fun fire(value: T) {
        listeners.get().forEach { log.catch { it(value) } }
    }


    override fun advise(lifetime : Lifetime, handler: (T) -> Unit) {
        advise0(listeners, lifetime, handler)
    }


    private fun advise0(queue:AtomicReference<Array<(T) -> Unit>>, lifetime : Lifetime, handler: (T) -> Unit) {
        if (lifetime.isTerminated) return

        lifetime.bracket(
                {
                    queue.getAndUpdate { arr ->
                        if (arr.contains(handler)) throw IllegalArgumentException("Duplicate handler: $handler")
                        arr.insert(handler, arr.size)
                    }
                },
                {
                    queue.getAndUpdate { arr ->
                        arr.remove (handler).apply { if (equals(arr)) throw IllegalArgumentException("No handler: $handler") }
                    }
                }
        )
    }


    class Void(val inner: ISignal<Boolean>) : IVoidSignal {
        constructor() : this(Signal<Boolean>())
        override fun advise(lifetime: Lifetime, handler: () -> Unit) = inner.advise(lifetime, { handler() })
        override fun fire() = inner.fire(true)
    }
}

