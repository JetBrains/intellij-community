fun foo() {
   <caret>while (f1()) {
        f2()
    }
}

fun f1() = true
fun f2() {}

// EXISTS: f1()
// IGNORE_K2