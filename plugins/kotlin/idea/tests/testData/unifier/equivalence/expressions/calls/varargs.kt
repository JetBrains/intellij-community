fun bar(vararg n: Int) {}

fun foo() {
    <selection>bar(1, 2, 3)</selection>
    bar(1, 2)
    bar(1, 2, 3)
    bar(1, 3, 2)
    bar(*IntArray(3))
}