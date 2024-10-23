package test1

class Test {
    inner class Inner<caret> {
    }
}

fun test() {
    Test().Inner()
}