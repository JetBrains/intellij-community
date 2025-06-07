// DISABLE_ERRORS
class A

fun foo(a: A, other: A) {
    <selection>a.bar<Int>(1, 2)</selection>
    a.bar<Int>(2, 1)
    a.bar<Int>(1, 2)
    a.bar<Int, String>(1, 2)
    a.bar<Int>(1)
    other.bar<Int>(1, 2)
    bar<Int>(1, 2)
}
