object SomeObject {
    operator fun invoke(someString: String, someInt: Int, i: Int) {
        println("$someString, $someInt")
    }
}

val someObject = SomeObject("some-string1", 1, 42)

val someObject2 = SomeObject.invoke("some-string2", 2, 42)