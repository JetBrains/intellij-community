fun main() {
    val b = 10
    var c = 0
    fun localFun(a: Int): Int {
        c = 100
        return a * b * c
    }
    //Breakpoint!
    val x = 1
}

// EXPRESSION: ::localFun.name
// RESULT: "localFun": Ljava/lang/String;

// EXPRESSION: ::localFun.invoke(2)
// RESULT: 2000: I