package testing

class A : JavaClass {
    override fun foo() = ""
}

class B : JavaClass by A()

class C : JavaClass by A() {
    override fun foo() = ""
}