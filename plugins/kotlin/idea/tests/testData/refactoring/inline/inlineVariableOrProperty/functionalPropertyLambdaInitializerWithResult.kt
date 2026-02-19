
class Declaration() {
    val funVal0 = { 0 }
}

fun call() {
    val declaration = Declaration()
    val i = declaration.fun<caret>Val0()
}