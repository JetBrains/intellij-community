fun main() {
    foo(fu<caret>n() {
        //do some stuff
    })
}

fun foo(action: () -> Unit) {}
