package smartStepIntoPropertyGetterReference

import kotlin.reflect.KProperty

class Delegate {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        return "TEXT"
    }
}

fun accessGetter(f: () -> Any) {
    f()
}

val delegatedPropery by Delegate()

val str1: String
    get() {
        return "TEXT"
    }

val str2
    get() = "TEXT"

val str3: String
    @JvmName("doStuff")
    get() {
        return "TEXT"
    }

val isBoolean: Boolean
    get() {
        return true
    }

class A {
    val delegatedPropery by Delegate()

    val str1: String
        get() {
            return "TEXT"
        }

    val str2
        get() = "TEXT"

    val str3: String
        @JvmName("doStuff")
        get() {
            return "TEXT"
        }

    val isBoolean: Boolean
        get() {
            return true
        }
}

fun main() {
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(::str1)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(::str2)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(::str3)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(::isBoolean)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(::delegatedPropery)

    val a = A()
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(a::str1)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(a::str2)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(a::str3)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(a::isBoolean)
    // SMART_STEP_INTO_BY_INDEX: 2
    // RESUME: 1
    //Breakpoint!
    accessGetter(a::delegatedPropery)
}
