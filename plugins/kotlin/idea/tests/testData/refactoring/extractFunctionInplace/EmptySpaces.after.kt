fun foo() {
    var v = 1..10


    if (bool<caret>(v)) return


    print(10)
}

private fun bool(v: IntRange): Boolean {
    v.forEach {
        if (it == 3) {
            return true
        }
    }
    return false
}