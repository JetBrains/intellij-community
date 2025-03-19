
class Declaration() {
    val funVal0 = fun(): Int {
        return 0
    }
}

fun call() {
    val declaration = Declaration()
    val i = declaration.funV<caret>al0()
}

// IGNORE_K1