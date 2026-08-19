fun x(a: Int, b: Int) {}
fun y() {
    x(// NO_CONVERSION_EXPECTED
        a = 1, b = 2)
}
