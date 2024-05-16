package p1

import p2.Outer.Inner

fun caller() {
    called()
}

fun called() {
    val typed: Set<Inner> = <selection>setOf(Inner.V1, Inner.V2)</selection>
    println(typed)
}

// IGNORE_K1