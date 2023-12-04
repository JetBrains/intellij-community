fun foo() {
    val sum = f1() +
      <caret>f2() + f3() +
      f4()
}

fun f1() = 10
fun f2() = 20
fun f3() = 30
fun f4() = 40

// EXISTS: f2(), f3(), f4()
// IGNORE_K2
