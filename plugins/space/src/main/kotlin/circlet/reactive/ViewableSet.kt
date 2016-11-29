package circlet.reactive

import klogging.*
import runtime.klogger.*
import runtime.lifetimes.*
import java.util.*

private val log = KLoggers.logger("app-idea/ViewableSet.kt")

class ViewableSet<T:Any> : IMutableViewableSet<T> {
    override fun add(element: T): Boolean {
        if (!set.add(element)) return false;
        change.fire(IViewableSet.Event(AddRemove.Add, element))
        return true;
    }

    private inline fun bulkOr(elements: Collection<T>, fn: (T) -> Boolean) = elements.fold(false) { acc, elt -> acc or fn(elt)}

    override fun addAll(elements: Collection<T>) = bulkOr(elements) {add(it)}

    override fun clear() {
        with(iterator()) { while(hasNext()) {
            next()
            remove()
        }}
    }

    override fun iterator(): MutableIterator<T> {
        return object:MutableIterator<T> {
            val delegate = set.iterator()
            var current : T? = null
            override fun remove() {
                delegate.remove()
                change.fire(IViewableSet.Event(AddRemove.Remove, current!!))
            }

            override fun hasNext(): Boolean = delegate.hasNext()
            override fun next(): T = delegate.next().apply { current = this }
        }
    }

    override fun remove(element: T): Boolean {
        if (!set.remove(element)) return false;
        change.fire(IViewableSet.Event(AddRemove.Remove, element))
        return true;
    }

    override fun removeAll(elements: Collection<T>) = bulkOr(elements) {remove(it)}

    override fun retainAll(elements: Collection<T>): Boolean {
        val iterator = iterator()
        var modified = false
        while (iterator.hasNext()) {
            if (!elements.contains(iterator.next())) {
                iterator.remove()
                modified = true
            }
        }
        return modified

    }

    private val set = LinkedHashSet<T>()
    private val change = Signal<IViewableSet.Event<T>>()

    override fun advise(lifetime: Lifetime, handler: (IViewableSet.Event<T>) -> Unit) {
        forEach { log.catch { handler(IViewableSet.Event(AddRemove.Add, it)) } }
        change.advise(lifetime, handler)
    }

    override val size: Int
        get() = set.size

    override fun contains(element: T): Boolean = set.contains(element)

    override fun containsAll(elements: Collection<T>): Boolean = set.containsAll(elements)

    override fun isEmpty(): Boolean = set.isEmpty()

}
