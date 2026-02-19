// IGNORE_K1
package p.q.r

val b = <selection>unresolved</selection> + 1

fun foo() {
    unresolved - 1
    (unresolved) - 1
    (anotherUnresolved) - 1

    unresolved()
    1 unresolved 2
    p.q.r.unresolved
}