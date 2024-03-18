fun main() {
    <caret>f1({ println("l1") }, { println("l2") })
}

fun f1(l1: () -> Unit, l2: () -> Unit) {}

// EXISTS: f1(() -> Unit\, () -> Unit), f1: l1.invoke(), f1: l2.invoke()
// IGNORE_K2
