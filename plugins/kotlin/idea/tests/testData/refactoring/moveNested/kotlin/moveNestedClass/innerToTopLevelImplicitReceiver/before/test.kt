package test1

class Test {

    fun bar() {

    }

    inner class Inner<caret> {
        fun foo() {
            bar()
        }
    }

}