package circlet.reactive

import runtime.*
import runtime.lifetimes.*

val<T> IReadonlyProperty<T>.hasValue : Boolean get() = this.maybe.hasValue


//boolean logic
fun IReadonlyProperty<Boolean>.whenTrue(lifetime: Lifetime, action: (Lifetime) -> Unit) {
    view(lifetime, {lf, flag -> if (flag) action(lf)})
}

fun IReadonlyProperty<Boolean>.whenFalse(lifetime: Lifetime, action: (Lifetime) -> Unit) {
    view(lifetime, {lf, flag -> if (!flag) action(lf)})
}

val IReadonlyProperty<Boolean>.hasTrueValue: Boolean
    get() = this.hasValue && this.value

fun <T1, T2, TRes> IReadonlyProperty<T1>.compose(lifetime: Lifetime, other: IReadonlyProperty<T2>, composer: (T1, T2) -> TRes) : IReadonlyProperty<TRes> {
    val res = Property<TRes>()
    advise(lifetime) { if (other.hasValue) res.set(composer(it, other.value))}
    other.advise(lifetime) { if (hasValue) res.set(composer(value, it))}
    return res
}

fun IReadonlyProperty<Boolean>.and(lifetime: Lifetime, other: IReadonlyProperty<Boolean>) = this.compose(lifetime, other, {a, b -> a && b})
fun IReadonlyProperty<Boolean>.and(other: IReadonlyProperty<Boolean>) = and(Lifetime.Eternal, other)

fun IReadonlyProperty<Boolean>.or(lifetime: Lifetime, other: IReadonlyProperty<Boolean>) = this.compose(lifetime, other, {a, b -> a || b})
fun IReadonlyProperty<Boolean>.or(other: IReadonlyProperty<Boolean>) = or(Lifetime.Eternal, other)

fun IReadonlyProperty<Boolean>.not(lifetime: Lifetime) = this.map(lifetime, {a -> !a})
fun IReadonlyProperty<Boolean>.not() = not(Lifetime.Eternal)

fun <TSource, TResult> List<IReadonlyProperty<TSource>>.foldRight(lifetime: Lifetime, initial: TResult, func: (TSource, TResult) -> TResult): Property<TResult> {
    val property = Property<TResult>()
    for (p in this) {
        p.advise(lifetime) {
            val value = this.foldRight(initial) { x, acc -> if (x.hasValue) func(x.value, acc) else acc }
            property.set(value)
        }
    }
    return property
}

fun <TSource, TResult> Iterable<IReadonlyProperty<TSource>>.fold(lifetime: Lifetime, initial: TResult, func: (TResult, TSource) -> TResult): Property<TResult> {
    val property = Property<TResult>()
    for (p in this) {
        p.advise(lifetime) {
            val value = this.fold(initial) { acc, x -> if (x.hasValue) func(acc, x.value) else acc }
            property.set(value)
        }
    }
    return property
}

fun <T> Iterable<IReadonlyProperty<T>>.all(lifetime: Lifetime, predicate: (T) -> Boolean): IReadonlyProperty<Boolean> {
    return this.fold(lifetime, true) { acc, x -> acc && predicate(x) }
}

fun <T> Iterable<IReadonlyProperty<T>>.any(lifetime: Lifetime, predicate: (T) -> Boolean): IReadonlyProperty<Boolean> {
    return this.fold(lifetime, false) { acc, x -> acc || predicate(x) }
}

fun Iterable<IReadonlyProperty<Boolean>>.any(lifetime: Lifetime): IReadonlyProperty<Boolean> {
    return this.fold(lifetime, false) { acc, x -> acc || x }
}

fun Iterable<IReadonlyProperty<Boolean>>.all(lifetime: Lifetime): IReadonlyProperty<Boolean> {
    return this.fold(lifetime, true) { acc, x -> acc && x }
}

fun <TSource, TTarget> IReadonlyProperty<TSource>.flowInto(lifetime: Lifetime, target: IProperty<TTarget>, converter: (TSource) -> TTarget)
{
    advise(lifetime) { target.value = converter(it) }
}

fun <TSource, TTarget> IReadonlyProperty<TSource>.map(lifetime: Lifetime, converter: (TSource) -> TTarget) : IProperty<TTarget> {
    return Property<TTarget>().apply { this@map.flowInto(lifetime, this, converter) }
}

fun <T> IProperty<T>.set(value : T) {this.value = value}

@Deprecated("Use `set` or property access", replaceWith = ReplaceWith(" = x"))
fun <T> IProperty<T>.setValue(value : T) {this.value = value} //for backward compatibility

fun <T : Any> IReadonlyProperty<T?>.adviseNotNull(lifetime: Lifetime, handler: (T) -> Unit) = this.advise(lifetime, { if (it != null) handler(it)})

fun <T : Any> IReadonlyProperty<T?>.adviseNull(lifetime: Lifetime, handler: () -> Unit) = this.advise(lifetime, { if (it == null) handler()})

fun <T : Any> IViewable<T?>.viewNotNull(lifetime : Lifetime, handler : (Lifetime, T) -> Unit) = this.view(lifetime, {lf, v -> if (v != null) handler(lf, v)})

fun <T> IViewable<T>.view(handler : (Lifetime, T) -> Unit) =this.view(Lifetime.Eternal, handler)

fun <T : Any> IViewable<T?>.viewNotNull(handler : (Lifetime, T) -> Unit) = this.viewNotNull(Lifetime.Eternal, handler)

@Deprecated("Use overload with lifetime instead")
fun <T> ISink<T>.advise(handler : (T) -> Unit) = this.adviseEternal(handler)

fun <T> ISink<T>.adviseEternal(handler : (T) -> Unit) = this.advise(Lifetime.Eternal, handler)

fun IVoidSink.adviseEternal(handler : () -> Unit) = this.advise(Lifetime.Eternal, handler)

fun <T> IProperty<T?>.setValue(lifetime: Lifetime, value : T?) {
    this.value = value
    lifetime.add {
        this.value = null
    }
} //for backward compatibility


// bind property to UI control
fun <T> IProperty<T>.bind(lifetime: Lifetime, setValue: (value: T) -> Unit, valueUpdated: ((value: T) -> Unit) -> Unit = {}) {
    val guard = Boxed(false)

    advise(lifetime) {
        if (!guard.value) setValue(it)
    }

    valueUpdated { v ->
        lifetime.ifAlive {
            assert(!guard.value)

            try {
                guard.value = true
                value = v
            } finally {
                guard.value = false
            }
        }
    }
}

fun <T> IProperty<T>.valueOrDefault(default: T) : T {
    if (this.hasValue)
        return this.value
    return default
}
