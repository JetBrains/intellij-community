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

    private var uIntProp: UInt = 42u
    private var intNProp: IntN = IntN(44)
    private var strProp: OverStr = OverStr("")
}
fun main() {
    Main().foo()
}

// EXPRESSION: uIntProp
// RESULT: instance of kotlin.UInt(id=ID): Lkotlin/UInt;

// EXPRESSION: uIntProp.toInt()
// RESULT: 42: I

// EXPRESSION: uIntProp = 43u
// RESULT: VOID_VALUE

// EXPRESSION: uIntProp.toInt()
// RESULT: 43: I


// EXPRESSION: intNProp
// RESULT: instance of IntN(id=ID): LIntN;

// EXPRESSION: intNProp.x!!
// RESULT: 44: I

// EXPRESSION: intNProp = IntN(45)
// RESULT: VOID_VALUE

// EXPRESSION: intNProp.x!!
// RESULT: 45: I


// EXPRESSION: strProp
// RESULT: instance of OverStr(id=ID): LOverStr;

// EXPRESSION: strProp.x!!
// RESULT: "": Ljava/lang/String;

// EXPRESSION: strProp = OverStr("abacaba")
// RESULT: VOID_VALUE

// EXPRESSION: strProp.x!!
// RESULT: "abacaba": Ljava/lang/String;