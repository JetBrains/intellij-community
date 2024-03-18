package test

import test.Outer.Nested

class Outer {
    class Nested
}

fun usage() {
    <selection>Outer.Nested()</selection>
}