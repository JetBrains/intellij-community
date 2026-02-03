package bar

import foo.other

fun bar(foo: Int) {
    other(other(other(1, 2), other(3, 4)), other(other(1, 2), other(3, 4)))
}