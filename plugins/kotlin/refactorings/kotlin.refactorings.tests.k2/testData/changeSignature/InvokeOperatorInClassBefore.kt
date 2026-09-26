class SomeClass {
    operator fun invo<caret>ke(someString: String, someInt: Int) {
        println("$someString, $someInt")
    }
}

val someClass = SomeClass()
val someObject = someClass("some-string1", 1)
val someObject2 = someClass.invoke("some-string2", 2)