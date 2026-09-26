package testing

class A : JavaClass {
    override fun bar() = ""
}

class B : JavaClass by A()

class C : JavaClass by A() {
    override fun bar() = ""
}