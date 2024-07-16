fun foo() {
    val a = A()
   <caret>a[1]
}

class A {
    operator fun get(i: Int) = 1
}

// EXISTS: get(Int)
