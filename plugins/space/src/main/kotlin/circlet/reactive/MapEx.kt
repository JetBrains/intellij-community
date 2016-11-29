package circlet.reactive

import runtime.collections.*
import runtime.lifetimes.*

//todo remove
public fun <K: Any, V: Any> IViewableMap<K, V>.tryGetKeyByValue(v: V) : K? = this.asSequence().firstOrNull { it.value == v }?.key

public fun <K: Any, V: Any> IMutableViewableMap<K, V>.getOrCreateWithLifetime(k: K, make: (K) -> V) : Lifetimed<V> {
    val v = this.getOrCreate(k, make)

    //ok, now really bullshit part
    var res : Lifetimed<V>? = null
    val def = Lifetime.create(Lifetime.Eternal)
    view(def.lifetime, { valueLifetime, vv ->
        if (vv.key == k) {
            assert(v == vv.value)
            res = Lifetimed(valueLifetime, v)
            valueLifetime += {def.terminate()}
        }
    })
    return res!!
}


