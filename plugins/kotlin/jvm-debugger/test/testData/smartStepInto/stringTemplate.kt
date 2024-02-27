fun foo() {
    <caret>f2("aaa${f1()}")
}

fun f1() = "1"
fun f2(s: String) {}

// EXISTS: f2(String), f1()
