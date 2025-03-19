class Declaration() {
    val funVa<caret>l1 = { x: String -> x.length }
}
fun call() {
    val declaration = Declaration()
    val str = declaration.funVal1("cd")
}