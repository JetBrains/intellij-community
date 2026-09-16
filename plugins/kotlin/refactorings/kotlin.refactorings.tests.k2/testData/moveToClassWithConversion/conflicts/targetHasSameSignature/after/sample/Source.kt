package sample

class Target {
    fun foo() {
        println("existing")
    }

    fun foo() {
        println(this)
    }
}

