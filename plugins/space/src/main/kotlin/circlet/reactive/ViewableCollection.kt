//package circlet.reactive
//
//import runtime.lifetimes.*
//import runtime.reactive.*
//
//
///**
// * Created by Alexander.Kirsanov on 8/13/2015.
// */
//// R#-like implementation of viewableCollection. It's quite heavy (memory traffic)
//class ViewableCollection<T>(private val myLifetime: Lifetime): IViewable<T>, Iterable<T> {
//
//    private val viewers = hashMapOf<((Lifetime, T) -> kotlin.Unit), Lifetime>()
//    private val values = arrayListOf<T>()
//    private val valueLifeTimeDefs = hashMapOf<T, LifetimeDefinition>()
//
//    fun count(): Int = values.count();
//    @Suppress("UNCHECKED_CAST")
//    fun toArray(): Array<T> = values.toArray() as Array<T>
//    fun remove(key: T): Boolean {
//        valueLifeTimeDefs[key]?.terminate().let{ valueLifeTimeDefs.remove(key)}
//        return values.remove(key)
//    }
//
//    fun add(lifetime: Lifetime, value: T) {
//        if (!values.add(value)){
//            throw IllegalArgumentException("Value already exists: $value")
//        }
//        lifetime += { remove(value) }
//        for ((k,v) in viewers){
//            view(v, value, k)
//        }
//    }
//
//    private fun getValueLifetime(value: T): Lifetime {
//        val lifetime = valueLifeTimeDefs[value]?.lifetime
//        if (lifetime == null) {
//            val newDef = Lifetime.create(myLifetime)
//            valueLifeTimeDefs.put(value, newDef)
//            return newDef.lifetime
//        }
//        return lifetime
//    }
//
//    private fun view(viewLifetime: Lifetime, value: T, observer: (Lifetime, T) -> Unit){
//        try {
//            observer(getValueLifetime(value).intersect(viewLifetime), value)
//        }
//        catch (e: Throwable) { // Execute each handler, isolate exceptions so that all of them could get executed
//            //logger?.error("handler in viewableCollection threw an exception", e) :todo make proper logging
//        }
//    }
//
//    override fun view(lifetime: Lifetime, handler: (Lifetime, T) -> Unit) {
//        val viewLifetime = myLifetime.intersect(lifetime)
//        viewers.put(handler, viewLifetime)
//        viewLifetime += { viewers.remove(handler)}
//        for(v in values){
//            view(viewLifetime, v, handler)
//        }
//    }
//
//    override fun iterator(): Iterator<T> = values.iterator()
//
//    fun tryGetLifetime(entity: T): Lifetime? {
//        return valueLifeTimeDefs[entity]?.lifetime
//    }
//}
