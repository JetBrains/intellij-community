//package circlet.reactive
//
//import runtime.lifetimes.*
//import runtime.reactive.*
//
//fun <T : Any> IViewableSet<T>.createIsEmpty(lifetime: Lifetime): IReadonlyProperty<Boolean> {
//    val property = Property(this.isEmpty())
//    this.advise(lifetime) { e -> property.set(this.isEmpty()) }
//    return property
//}
//
//fun <T : Any> IViewableSet<T>.createIsEmpty() = createIsEmpty(Lifetime.Eternal)
//fun <T : Any> IViewableSet<T>.createIsNotEmpty(lifetime: Lifetime) = createIsEmpty(lifetime).not()
//fun <T : Any> IViewableSet<T>.createIsNotEmpty() = createIsNotEmpty(Lifetime.Eternal)
//
//
//fun <K : Any, V: Any> IViewableMap<K, V>.createIsEmpty(lifetime: Lifetime): IReadonlyProperty<Boolean> {
//    val property = Property(this.isEmpty())
//    this.advise(lifetime) { property.set(this.isEmpty()) }
//    return property
//}
//
//fun <K : Any, V: Any> IViewableMap<K, V>.createIsEmpty() = createIsEmpty(Lifetime.Eternal)
//fun <K : Any, V: Any> IViewableMap<K, V>.createIsNotEmpty(lifetime: Lifetime) = createIsEmpty(lifetime).not()
//fun <K : Any, V: Any> IViewableMap<K, V>.createIsNotEmpty() = createIsNotEmpty(Lifetime.Eternal)
