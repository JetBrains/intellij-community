class It(var x: Int) {
    fun next(id: String): It {
        println("Hi!")
        return It(x + 1)
    }
}

fun main() {
    It(0).next("a").next("b")
        <caret>.next("c").next("d")
        .next("e").next("f")

}

// EXISTS: next(String)_0, next(String)_1, next(String)_2, next(String)_3
