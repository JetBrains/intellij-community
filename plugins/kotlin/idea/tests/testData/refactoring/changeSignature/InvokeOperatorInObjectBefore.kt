object SomeObject {
    operator fun invo<caret>ke(someString: String, someInt: Int) {
        println("$someString, $someInt")
    }
}

val someObject = SomeObject("some-string1", 1)

val someObject2 = SomeObject.invoke("some-string2", 2)