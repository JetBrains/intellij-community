fun main() {
    foo1 {
        fun localFun() {
            return
        }

        class X {
            fun localClassMemberFun() {
                return
            }
        }
    }
}

inline fun foo1(noinline block: () -> Unit) {
    foo2 {
        block();
        repeat(listOf(1).size) {
            return@repeat
        }
        return@foo2 42
    }
}

inline fun foo2(noinline block: () -> Int) {
    // EXPRESSION: block()
    // RESULT: 42: I
    //Breakpoint!
    val x = 1
}