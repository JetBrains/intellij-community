package circlet.reactive

import runtime.collections.*
import runtime.lifetimes.*

interface ISink<out T> {
    fun advise(lifetime : Lifetime, handler : (T) -> Unit)
}

interface IVoidSink {
    fun advise(lifetime : Lifetime, handler : () -> Unit)
}

interface IReadonlyProperty<out T> : ISink<T>, IViewable<T> {
    val maybe: Maybe<T>
    val value: T //could lead to exception
    val change: ISink<T>

    override fun view(lifetime : Lifetime, handler : (Lifetime, T) -> Unit) {
        if (lifetime.isTerminated) return

        // nested lifetime is needed due to exception that could be thrown
        // while viewing a property change right at the moment of <param>lifetime</param>'s termination
        // but before <param>handler</param> gets removed (e.g. p.view(lf) { /*here*/ }; lf += { p.set(..) })
        val lf = if (!lifetime.isEternal) Lifetime.create(lifetime).lifetime else lifetime
        runtime.reactive.SequentialLifetimes(lf).let {
            advise(lf) {v ->
                handler(it.next(), v)
            }
        }
    }
}


interface ISource<in T> {
    fun fire(value : T)
}

//Touching the Void
interface IVoidSource {
    fun fire()
}


interface ISignal<T> : ISource<T>, ISink<T>

interface IVoidSignal : IVoidSource, IVoidSink

interface IViewable<out T> {
    fun view(lifetime : Lifetime, handler : (Lifetime, T) -> Unit)
}

interface IProperty<T> : IReadonlyProperty<T>, IViewable<T> {
    override var value : T
    operator fun timesAssign(v : T) { value = v }
}

enum class AddRemove {Add, Remove}


interface IViewableSet<T : Any> : Set<T>, IViewable<T>, ISink<IViewableSet.Event<T>> {
    data class Event<T>(val kind: AddRemove, val value: T)

    fun advise(lifetime: Lifetime, handler: (AddRemove, T) -> Unit) = advise(lifetime) {evt -> handler(evt.kind, evt.value)}

    override fun view(lifetime: Lifetime, handler: (Lifetime, T) -> Unit) {
        val lifetimes = hashMapOf<T, LifetimeDefinition>()
        advise(lifetime) { kind, v ->
            when (kind) {
                AddRemove.Add -> {
                    val def = lifetimes.putUnique(v, Lifetime.create(lifetime))
                    handler(def.lifetime, v)
                }
                AddRemove.Remove -> lifetimes.remove(v)!!.terminate()
            }
        }
    }
}

interface IMutableViewableSet<T:Any> : MutableSet<T>, IViewableSet<T>

data class KeyValuePair<K,V>(override val key: K, override val value: V) : Map.Entry<K, V>

sealed class Event<K,V>(val key: K) {
    class Add<K,V>   (key: K,                   val newValue : V) : Event<K,V>(key)
    class Update<K,V>(key: K, val oldValue : V, val newValue : V) : Event<K,V>(key)
    class Remove<K,V>(key: K, val oldValue : V                  ) : Event<K,V>(key)

    val newValueOpt: V? get() = when (this) {
        is Event.Add    -> this.newValue
        is Event.Update -> this.newValue
        else -> null
    }
}

interface IViewableMap<K : Any, V:Any> : Map<K, V>, IViewable<Map.Entry<K, V>>, ISink<Event<K, V>> {

    override fun view(lifetime: Lifetime, handler: (Lifetime, Map.Entry<K, V>) -> Unit) {
        val lifetimes = hashMapOf<Map.Entry<K, V>, LifetimeDefinition>()
        adviseAddRemove(lifetime) { kind, key, value ->
            val entry = KeyValuePair(key, value)
            when (kind) {
                AddRemove.Add -> {
                    val def = lifetimes.putUnique(entry, Lifetime.create(lifetime))
                    handler(def.lifetime, entry)
                }
                AddRemove.Remove -> lifetimes.remove(entry)!!.terminate()
            }
        }
    }

    fun adviseAddRemove(lifetime: Lifetime, handler: (AddRemove, K, V) -> Unit) {
        advise(lifetime) { when (it) {
            is Event.Add -> handler(AddRemove.Add, it.key, it.newValue)
            is Event.Update -> {
                handler(AddRemove.Remove, it.key, it.oldValue)
                handler(AddRemove.Add, it.key, it.newValue)
            }
            is Event.Remove -> handler(AddRemove.Remove, it.key, it.oldValue)
        }}
    }

    fun view(lifetime: Lifetime, handler: (Lifetime, K, V) -> Unit) = view(lifetime, {lf, entry -> handler(lf, entry.key, entry.value)})
}

interface IMutableViewableMap<K : Any, V: Any> : MutableMap<K, V>, IViewableMap<K, V>
