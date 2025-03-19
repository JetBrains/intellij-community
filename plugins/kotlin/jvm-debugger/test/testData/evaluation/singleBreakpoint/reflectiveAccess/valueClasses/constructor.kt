@JvmInline
value class IntN(val x : Int?) {
    fun toInt() = x!!
}

@JvmInline
value class OverStr(val x: String)

class A private constructor(val x: UInt)
class B private constructor(val x: IntN)
class C private constructor(val x: OverStr)

@JvmInline
value class D private constructor(val x: IntN)


class Main {
    fun foo(): Int {
        //Breakpoint!
        return 0
    }
}

private var uIntProp: UInt = 42u
private var intNProp: IntN = IntN(44)
private var strProp: OverStr = OverStr("")

fun main() {
    Main().foo()
}

// EXPRESSION: A(42u).x.toInt()
// RESULT: 42: I

// EXPRESSION: B(IntN(42)).x.x!!
// RESULT: 42: I

// EXPRESSION: C(OverStr("abacaba")).x.x
// RESULT: "abacaba": Ljava/lang/String;

// EXPRESSION: D(IntN(42)).x.x!!
// RESULT: 42: I