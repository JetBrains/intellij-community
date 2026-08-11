interface I1 {
}

interface I2 {
}

abstract class AClass {
}

class A {
    private val field1: AClass = object : AClass(), I1, I2 {}
    private val field2: I1 = object : AClass(), I1, I2 {}
    private val field3: I2 = object : AClass(), I1, I2 {}
}
