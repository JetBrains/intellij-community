open class T

fun foo(): T {
    <selection>val o = object: T() {}</selection>
    return o
}

// IGNORE_K1