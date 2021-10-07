internal class A {
    fun foo() {
        val charDigit = '4'
        val radix = 10
        val intDigit = charDigit.digitToIntOrNull(radix) ?: -1
        val literalRadix = charDigit.digitToIntOrNull() ?: -1
    }
}
