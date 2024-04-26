open class MyClass {
    constructor(s1: String, int<caret>: Int, s2: String)
}

class Child(s3: String) : MyClass(s3, s3.length, "dummy")
