package sample

class Target {
    fun foo() {
        println(this)
    }
}

class Src {

    fun callerInsideSrc(target: Target) {
        target.foo()
    }
}

