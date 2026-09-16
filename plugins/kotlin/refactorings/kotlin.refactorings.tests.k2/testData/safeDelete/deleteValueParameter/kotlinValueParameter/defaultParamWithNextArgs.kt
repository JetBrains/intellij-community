fun myFunction(first: String, sec<caret>ond: Boolean = true, last: String) {

}

fun usage() {
    myFunction("str", false, "str2")

    myFunction("str", "str3")
}
