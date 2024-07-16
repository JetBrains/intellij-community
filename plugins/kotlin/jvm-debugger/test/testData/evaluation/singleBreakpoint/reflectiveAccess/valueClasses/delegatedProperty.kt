import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

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

    private var privateDelegatedPropertyUInt: UInt by object : ReadWriteProperty<Main, UInt> {

        var backingField = 0u

        override operator fun setValue(thisRef: Main, property: KProperty<*>, value: UInt) {
            backingField = value
        }

        override fun getValue(thisRef: Main, property: KProperty<*>): UInt {
            return backingField
        }
    }

    private var privateDelegatedPropertyIntN: IntN by object : ReadWriteProperty<Main, IntN> {

        var backingField = IntN(0)

        override operator fun setValue(thisRef: Main, property: KProperty<*>, value: IntN) {
            backingField = value
        }

        override fun getValue(thisRef: Main, property: KProperty<*>): IntN {
            return backingField
        }
    }

    private var privateDelegatedPropertyStr: OverStr by object : ReadWriteProperty<Main, OverStr> {

        var backingField = OverStr("")

        override operator fun setValue(thisRef: Main, property: KProperty<*>, value: OverStr) {
            backingField = value
        }

        override fun getValue(thisRef: Main, property: KProperty<*>): OverStr {
            return backingField
        }
    }
}

fun main() {
    Main().foo()
}

// EXPRESSION: privateDelegatedPropertyUInt = 42u
// RESULT: VOID_VALUE

// EXPRESSION: privateDelegatedPropertyUInt
// RESULT: instance of kotlin.UInt(id=ID): Lkotlin/UInt;

// EXPRESSION: privateDelegatedPropertyUInt.toInt()
// RESULT: 42: I


// EXPRESSION: privateDelegatedPropertyIntN = IntN(42)
// RESULT: VOID_VALUE

// EXPRESSION: privateDelegatedPropertyIntN
// RESULT: instance of IntN(id=ID): LIntN;

// EXPRESSION: privateDelegatedPropertyIntN.toInt()
// RESULT: 42: I


// EXPRESSION: privateDelegatedPropertyStr = OverStr("abacaba")
// RESULT: VOID_VALUE

// EXPRESSION: privateDelegatedPropertyStr
// RESULT: instance of OverStr(id=ID): LOverStr;

// EXPRESSION: privateDelegatedPropertyStr.x
// RESULT: "abacaba": Ljava/lang/String;