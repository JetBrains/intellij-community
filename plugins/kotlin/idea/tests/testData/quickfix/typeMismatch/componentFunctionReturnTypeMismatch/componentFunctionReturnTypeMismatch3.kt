// "Remove explicitly specified return type of called function 'A.component2'" "true"
abstract class A {
    abstract operator fun component1(): Int
    abstract operator fun component2(): Int
}

fun foo(a: A) {
    val (w: Int, x: Unit) = a<caret>
}