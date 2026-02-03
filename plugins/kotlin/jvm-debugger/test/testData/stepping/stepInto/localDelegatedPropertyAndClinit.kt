import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class MyDelegate : ReadOnlyProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = "OK"
}

class MyClass {
    fun foo() {
        val x by MyDelegate()
    }
}

fun main() {
    // STEP_INTO: 2
    //Breakpoint!
    val y = MyClass()
}