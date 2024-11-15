interface Bar

fun <T, R> foo(receiver: T, block: T.() -> R): R {
    return receiver.block()
}

fun Bar.function(top: String = "", builder: Bar.() -> Unit) {
    foo("") {
        builder()
        function(top, builder)
    }
}

fun Bar.function2() {
    funct<caret>ion {}
}