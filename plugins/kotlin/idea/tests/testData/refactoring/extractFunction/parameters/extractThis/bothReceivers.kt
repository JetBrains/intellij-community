// PARAM_TYPES: B, kotlin.Any
// PARAM_DESCRIPTOR: value-parameter b: B defined in n
class B
class A {
    fun B.m() {}
}

fun n(a: A, b: B) {
    with(a) {
        <selection>b.m()</selection>
    }
}

// IGNORE_K1