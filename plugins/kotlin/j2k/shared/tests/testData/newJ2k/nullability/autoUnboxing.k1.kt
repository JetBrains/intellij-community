internal class C {
    fun testAssignment(i1: Int, i2: Int, i3: Int, i4: Int, i5: Int?) {
        var i4 = i4
        var i5 = i5
        var j = i1
        j += i2
        j = j shr i3
        i4 += j
        i5 = j // not applicable
    }

    fun testUnaryExpr(i1: Int, i2: Int, i3: Int, i4: Int) {
        var i1 = i1
        var i2 = i2
        var i3 = i3
        var i4 = i4
        println(++i1)
        println(i2++)
        println(--i3)
        println(i4--)
    }

    fun testBinaryExpr(i1: Int, i2: Int, i3: Int, i4: Int, i5: Int) {
        println(i1 + 1)
        println(1 - i2)
        println(i3 * 1)
        println(1 / i4)
        println(i5 % 1)
    }

    fun testNumberComparison(i1: Int, i2: Int, i3: Int, i4: Int, i5: Int) {
        println(i1 == 1)
        println(1 != i2)
        println(i3 > 1)
        println(1 <= i4)
        println(i5 === i1) // not applicable
    }

    fun testBooleanComparison(b1: Boolean, b2: Boolean, b3: Boolean?, b4: Boolean, b5: Boolean) {
        println(b1 == true)
        println(false == b2)
        println(b3 === b4) // not applicable
        println(b3 != null) // not applicable
        println(b4 == b5)
    }

    fun testLogical(b1: Boolean, b2: Boolean, b3: Boolean, b4: Boolean) {
        println(b1 && b4)
        println(b4 || b2)
        println(!b3)
    }

    fun testConditionals(b1: Boolean, b2: Boolean, b3: Boolean, b4: Boolean) {
        if (b1) {
        }
        while (b2) {
        }
        while (b3) {
        }
        println(if (b4) "yes" else "no")
    }

    fun testMethodCall(i: Int) {
        takesPrimitiveInt(i)
    }

    // Not applicable for Strings
    fun testStrings(s1: String, i: Int) {
        println(s1 + i)
    }

    fun takesPrimitiveInt(i: Int) {
    }
}
