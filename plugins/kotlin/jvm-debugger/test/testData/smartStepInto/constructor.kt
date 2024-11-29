fun foo() {
    println("${A()} ${B()} ${C(1)} ${D()} ${E(1)} ${F()} ${G(1)} ${J()} ${K(1)} ${L()}")<caret>
}

class A
class B()
class C(val a: Int)
class D {
    constructor()
}
class E {
    constructor(i: Int)
}
class F {
    constructor() {

    }
}
class G {
    constructor(i: Int) {

    }
}
class J {
    init {

    }
}
class K(val a: Int) {
    init {

    }
}
class L {
    constructor() {

    }

    init {

    }
}

// EXISTS: constructor A()
// EXISTS: constructor B()
// EXISTS: constructor C(Int)
// EXISTS: constructor D()
// EXISTS: constructor E(Int)
// EXISTS: constructor F()
// EXISTS: constructor G(Int)
// EXISTS: constructor J()
// EXISTS: constructor K(Int)
// EXISTS: constructor L()
