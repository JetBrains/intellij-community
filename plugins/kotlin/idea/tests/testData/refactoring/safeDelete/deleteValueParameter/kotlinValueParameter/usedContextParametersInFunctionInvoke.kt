// COMPILER_ARGUMENTS: -Xcontext-parameters
// LANGUAGE_VERSION: 2.2

class C1
class C2

class Receiver

context(c1: C1, c<caret>2: C2)
fun client() {
    r()
}

context(c1: C1)
val r: Receiver
    get() = Receiver()

context(c2: C2)
operator fun Receiver.invoke() {
}

// IGNORE_K1