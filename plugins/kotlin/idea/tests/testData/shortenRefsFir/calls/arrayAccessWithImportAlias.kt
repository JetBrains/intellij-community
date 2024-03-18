package test

import dependency.foo as bar
import dependency.fooIndex as index

val i = 2

fun usage() {
    <selection>dependency.foo[dependency.fooIndex + i - 1]</selection>
}