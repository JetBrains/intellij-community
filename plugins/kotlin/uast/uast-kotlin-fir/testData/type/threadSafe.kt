import kotlin.reflect.KProperty

annotation class ThreadSafe

class UnsafeClass

@ThreadSafe
class NotThreadSafeDelegate {
    operator fun getValue(thisRef: Any, property: KProperty<*>): UnsafeClass {
        return UnsafeClass()
    }
}

@ThreadSafe
class SimpleSafeClass

@ThreadSafe
class ThreadSafeDelegate {
    operator fun getValue(thisRef: Any, property: KProperty<*>): SimpleSafeClass {
        return SimpleSafeClass()
    }
}

@ThreadSafe
class SafeClass(val message: String) {
    val k: SimpleSafeClass by lazy {
        SimpleSafeClass()
    }
    val l: SimpleSafeClass by ThreadSafeDelegate()

    companion object {
        @JvmStatic
        val s = UnsafeClass()
    }
}
