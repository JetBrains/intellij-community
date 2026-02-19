package privateToplevelProperty

private var topLevelProperty: Int = 0
    get() = 0
    set(value) { field += value }

open class C(val p: Int) {
    init {
        //Breakpoint!
        val x = topLevelProperty
        topLevelProperty = 4
    }
}

fun main(args: Array<String>) {
    C(1)
}

// From KT-52372
// The fragment compiler includes the project file in the fragment compilation,
// and rewrites the property access in the init block using the reflection API.

// EXPRESSION: p
// RESULT: 1: I