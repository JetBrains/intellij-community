package a

import b.Dependency

val keep = 1

fun test<caret>() {
    val a = Dependency()
}