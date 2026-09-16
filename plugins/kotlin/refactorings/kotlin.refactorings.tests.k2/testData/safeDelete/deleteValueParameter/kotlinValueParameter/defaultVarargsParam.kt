fun myFunction(first: String, vararg l<caret>ast: String = arrayOf("", ""), s: Int) {

}

fun m() {
    myFunction("1", s = 1)
    myFunction("1", "2", s = 1)
    myFunction("1", "2", "3", s = 1)
}
