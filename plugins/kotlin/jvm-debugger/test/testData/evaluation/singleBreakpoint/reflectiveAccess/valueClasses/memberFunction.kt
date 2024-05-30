@JvmInline
value class IntN(val x : Int?) {
    fun toInt() = x!!
}

@JvmInline
value class OverStr(val x: String)

class Main {
    fun foo(): Int {
        //Breakpoint!
        return 0
    }
    private fun getUInt(x: UInt) = x

    private fun getIntN(x : IntN) = x

    private fun getStr(x: OverStr) = x
}

fun main() {
    Main().foo()
}

// EXPRESSION: getUInt(2u)
// RESULT: instance of kotlin.UInt(id=ID): Lkotlin/UInt;

// EXPRESSION: getUInt(2u).toInt()
// RESULT: 2: I


// EXPRESSION: getIntN(IntN(3))
// RESULT: instance of IntN(id=ID): LIntN;

// EXPRESSION: getIntN(IntN(3)).x!!
// RESULT: 3: I


// EXPRESSION: getStr(OverStr("abacaba"))
// RESULT: instance of OverStr(id=ID): LOverStr;

// EXPRESSION: getStr(OverStr("abacaba")).x
// RESULT: "abacaba": Ljava/lang/String;