fun myFunction(first: String, sec<caret>ond: Boolean = true, vararg last: String) {

}

fun usage() {
    myFunction("str", false, "str2", "str22")
    myFunction("str", false, "str2")
    myFunction("str", false)

    myFunction("str")
    myFunction("str", "str3")
    myFunction("str", "str3", "str33")
}
