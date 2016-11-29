package circlet.reactive

import runtime.lifetimes.*


open class Property<T>() : IProperty<T> {

    constructor(defaultValue: T) : this() { value = defaultValue }

    override val change = Signal<T>()
    override var maybe: Maybe<T> = Maybe.None
        protected set

    override var value : T
        get() = maybe.orElseThrow { IllegalStateException ("Not initialized") }
        set(newValue) {
            maybe.let { if (it is Maybe.Just && newValue == it.value) return}
            maybe = Maybe.Just(newValue)
            change.fire(newValue)
        }

    override fun advise(lifetime: Lifetime, handler: (T) -> Unit) {
        if (lifetime.isTerminated) return

        if (maybe.hasValue) handler(value)
        change.advise(lifetime, handler)
    }

    fun resetValue() {
        maybe = Maybe.None
    }
}

class OneWriteProperty<T> : Property<T>() {
    override var value : T
        get() = super.value
        set(newValue) {
            maybe.let {
                if (it is Maybe.Just)
                    if (value == newValue) return
                    else throw IllegalStateException("OneWriteProperty already set with `$value`, but you try to rewrite it to `$newValue`")
            }
            super.value = newValue
        }

}
