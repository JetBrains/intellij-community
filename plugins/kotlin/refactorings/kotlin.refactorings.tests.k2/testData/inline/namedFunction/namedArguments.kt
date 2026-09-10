interface I {
    fun oldFun(option1: String = "", option2: Int = 0, option3: Int = -1) {
        newFun(option1, option2, option3, null)
    }

    fun newFun(option1: String = "", option2: Int = 0, option3: Int = -1, option4: String? = "x")
}

fun foo(i: I) {
    i.oldF<caret>un(option2 = 1)
}