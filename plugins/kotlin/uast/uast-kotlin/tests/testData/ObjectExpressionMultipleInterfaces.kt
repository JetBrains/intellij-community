interface I1 {
}

interface I2 {
}

abstract class AClass {
}

class A {
    private val field = object : AClass(), I1, I2 {
    }
}