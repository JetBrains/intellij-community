fun myFunction(first: String, sec<caret>ond: Boolean = true, last: () -> Unit) {

}

fun usage() {
    myFunction("str", false) {
        Unit
    }

    myFunction("str") {
        Unit
    }
}
