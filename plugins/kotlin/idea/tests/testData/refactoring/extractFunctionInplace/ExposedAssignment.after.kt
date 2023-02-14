fun sample(){
    val x = getX<caret>()

    println(x)
}

private fun getX(): Int {
    val x = 42
    println()
    return x
}