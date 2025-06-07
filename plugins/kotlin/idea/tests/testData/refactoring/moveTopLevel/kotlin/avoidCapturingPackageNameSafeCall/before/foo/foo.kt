package foo

class Other {
    fun other() {

    }
}

fun bar<caret>(other: Other?) {
    other?.other()
    Other()?.other()
}
