class A<T>(x : T) {
    val prop: Pair<Pair<T, T>, T> = (x to x) to x

    fun func(): Int{
        //Breakpoint!
        return 42
    }
}

fun main() {
    A("").func()
}

// EXPRESSION: prop
// RESULT: instance of kotlin.Pair(id=ID): Lkotlin/Pair;