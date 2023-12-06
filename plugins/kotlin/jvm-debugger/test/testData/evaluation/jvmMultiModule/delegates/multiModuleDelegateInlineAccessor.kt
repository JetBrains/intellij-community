// MODULE: jvm-lib
// FILE: myVeryOwnInlineDelegates.kt

package myVeryOwnInlineDelegates

import kotlin.reflect.KProperty

interface MyDelegate<T> {
    val value: T
}

class MyDelegateImpl<T>(override val value: T) : MyDelegate<T>

public inline operator fun <T> MyDelegate<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

fun <T> myDelegate(init: () -> T) = MyDelegateImpl(init())

// MODULE: jvm-app(jvm-lib)
// FILE: multiModuleDelegateInlineAccessor.kt

import myVeryOwnInlineDelegates.myDelegate
import myVeryOwnInlineDelegates.getValue

class Delegates {
    var text: String = ""

    val myMapping by myDelegate {
        mapOf(
            "Hello" to 1,
            "World" to 2
        )
    }

    fun mapped(i: Int) = myMapping.entries.first { it.value == i }.key

    fun foo(i: Int) {
        text = mapped(i)
        // EXPRESSION: text
        // RESULT: "World": Ljava/lang/String;
        //Breakpoint!
        println(text)
    }
}

fun main() {
    Delegates().foo(2)
}
