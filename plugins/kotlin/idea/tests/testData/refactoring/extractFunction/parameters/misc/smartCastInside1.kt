class C(val int: Int?)

fun test(x: Int, c: C): Int {
    val a =
        <selection>if (c.int != null) x + c.int else x</selection>
    return a * a
}

// IGNORE_K1