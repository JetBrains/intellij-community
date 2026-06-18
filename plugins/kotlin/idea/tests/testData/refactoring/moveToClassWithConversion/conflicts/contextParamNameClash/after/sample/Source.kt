package sample

class Target {
    fun foo(target: Target) {
        println(this)
        println(this) // KTIJ-39211
    }
}

