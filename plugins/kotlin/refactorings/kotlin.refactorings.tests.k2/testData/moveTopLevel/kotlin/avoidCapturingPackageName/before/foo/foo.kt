package foo

fun other() {

}

fun bar<caret>(foo: Int) {
    other()
}
