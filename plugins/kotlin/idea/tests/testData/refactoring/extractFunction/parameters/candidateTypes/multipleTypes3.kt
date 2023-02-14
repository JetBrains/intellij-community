// PARAM_TYPES: A
// PARAM_DESCRIPTOR: value-parameter o: kotlin.Any defined in foo

open class A {
    val a = 1
}

interface T {
    val t: Int
}

class B : A(), T {
    override val t: Int = 2
}

fun foo(o: Any) {
    val x = when (o) {
        is A -> {
            <selection>if (o is T) o.a + o.t else o.a</selection>
        }
        else -> o.hashCode()
    }
}