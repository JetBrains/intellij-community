package circlet.reactive

import klogging.*
import runtime.klogger.*
import runtime.lifetimes.*
import runtime.reactive.*

private val log = KLoggers.logger("app-idea/ViewableMap.kt")

class ViewableMap<K : Any, V : Any>() : IMutableViewableMap<K, V> {

    private val map = linkedMapOf<K, V>()
    private val change = Signal<Event<K, V>>()

    override fun advise(lifetime: Lifetime, handler: (Event<K, V>) -> Unit) {
        this.forEach { log.catch { handler(Event.Add(it.key, it.value)) } }
        change.advise(lifetime, handler)
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { put(it.key, it.value) }
    }

    override fun put(key: K, value: V): V? {
        val oldval = map.put(key, value)
        if (oldval != null) {
            if (oldval != value) change.fire(Event.Update(key, oldval, value))
        } else {
            change.fire(Event.Add(key, value))
        }
        return oldval
    }

    override fun remove(key: K): V? {
        val oldval = map.remove(key)
        if (oldval != null) change.fire(Event.Remove(key, oldval))
        return oldval
    }

    override fun clear() {
        val changes = arrayListOf<Event<K, V>>()
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            changes.add(Event.Remove(entry.key, entry.value))
            iterator.remove()
        }
        changes.forEach { change.fire(it) }
    }

    override val keys: MutableSet<K> get() = map.keys
    override val values: MutableCollection<V> get() = map.values
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = map.entries
    override val size: Int get() = map.size
    override fun isEmpty(): Boolean = map.isEmpty()
    override fun containsKey(key: K): Boolean = map.containsKey(key)
    override fun containsValue(value: V): Boolean = map.containsValue(value)
    override fun get(key: K): V? = map[key]

}


