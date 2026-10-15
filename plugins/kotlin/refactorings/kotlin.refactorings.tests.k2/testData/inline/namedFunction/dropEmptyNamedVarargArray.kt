fun varargFunction1(key: String, vararg params: Any) = varargFunction2(key = key, params = params)

fun varargFunction2(key: String, vararg params: Any) {
}

fun test() {
    vararg<caret>Function1("1")
    varargFunction1("2", 2)
}
