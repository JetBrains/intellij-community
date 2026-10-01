fun main() {
    foo(<selection>fun() {
        //do some stuff
    }</selection>)
}

fun foo(action: () -> Unit) {}
