// PARAM_DESCRIPTOR: val b: kotlin.Int defined in Subexpression.calculate, val c: kotlin.Int defined in Subexpression.calculate
// PARAM_TYPES: kotlin.Int
// PARAM_TYPES: kotlin.Int

class Subexpression {
    fun calculate() {
        val a = 1
        val b = 2
        val c = 3
        val result = a * <selection>(b + c)</selection> - a
        println(result)
    }
}