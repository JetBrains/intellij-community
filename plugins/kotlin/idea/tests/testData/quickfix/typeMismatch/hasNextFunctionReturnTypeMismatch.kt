// "Change return type of called function 'A.hasNext' to 'Boolean'" "true"
abstract class A {
    abstract operator fun hasNext(): Int
    abstract operator fun next(): Int
    abstract operator fun iterator(): A
}

fun test(notRange: A) {
    for (i in notRange<caret>) {}
}